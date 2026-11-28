package com.newsreview.workbench.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.newsreview.workbench.WorkbenchViewModel
import com.newsreview.workbench.ui.screens.AiReportScreen
import com.newsreview.workbench.ui.screens.ArticleDetailScreen
import com.newsreview.workbench.ui.screens.EditScreen
import com.newsreview.workbench.ui.screens.HomeScreen
import com.newsreview.workbench.ui.screens.LibraryScreen
import com.newsreview.workbench.ui.screens.LoginScreen
import com.newsreview.workbench.ui.screens.MaterialsScreen
import com.newsreview.workbench.ui.screens.MissingArticleScreen
import com.newsreview.workbench.ui.theme.AppColors

/** 路由表。稿件相关页面都用稿件 id 做参数，返回时不丢上下文。 */
object Routes {
    const val ARG_ARTICLE_ID = "articleId"
    const val ARG_PARAGRAPH = "paragraph"

    const val LOGIN = "login"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val MATERIALS = "materials"
    const val ARTICLE = "article/{$ARG_ARTICLE_ID}?paragraph={$ARG_PARAGRAPH}"
    const val AI = "ai/{$ARG_ARTICLE_ID}"
    const val EDIT = "edit/{$ARG_ARTICLE_ID}"

    fun article(id: String, paragraph: Int = -1) = "article/$id?paragraph=$paragraph"
    fun ai(id: String) = "ai/$id"
    fun edit(id: String) = "edit/$id"
}

/**
 * 界面外壳。只做三件事：全局 Snackbar、导航图、把 ViewModel 交给各页面。
 * 状态栏 / 导航栏的内边距交给各页面的 TopAppBar 与底部操作条自己处理，
 * 所以这里的 Scaffold 不占任何窗口内边距。
 */
@Composable
fun WorkbenchApp(vm: WorkbenchViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.notice) {
        val notice = state.notice
        if (notice != null) {
            snackbarHostState.showSnackbar(notice)
            vm.consumeNotice()
        }
    }

    Scaffold(
        containerColor = AppColors.PageBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box {
            NavHost(
                navController = navController,
                startDestination = Routes.LOGIN,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Routes.LOGIN) {
                    fun enterHome() {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                    LoginScreen(
                        vm = vm,
                        onEnter = { role ->
                            vm.login(role)
                            enterHome()
                        },
                        onServerEnter = { role, address, password ->
                            vm.loginServer(role, address, password, ::enterHome)
                        }
                    )
                }

                composable(Routes.HOME) {
                    HomeScreen(
                        vm = vm,
                        onOpenArticle = { id -> vm.openArticle(id) { navController.navigate(Routes.article(id)) } },
                        onImportWord = {
                            val id = vm.importSampleDocx()
                            if (id != null) navController.navigate(Routes.article(id))
                        },
                        onNewArticle = { navController.navigate(Routes.edit(vm.createBlankArticle())) },
                        onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                        onOpenMaterials = { navController.navigate(Routes.MATERIALS) },
                        onSwitchRole = {
                            vm.logout()
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.LIBRARY) {
                    LibraryScreen(vm = vm, onBack = { navController.popBackStack() })
                }

                composable(Routes.MATERIALS) {
                    MaterialsScreen(vm = vm, onBack = { navController.popBackStack() })
                }

                composable(
                    route = Routes.ARTICLE,
                    arguments = listOf(
                        navArgument(Routes.ARG_ARTICLE_ID) { type = NavType.StringType },
                        navArgument(Routes.ARG_PARAGRAPH) {
                            type = NavType.IntType
                            defaultValue = -1
                        }
                    )
                ) { entry ->
                    val id = entry.arguments?.getString(Routes.ARG_ARTICLE_ID).orEmpty()
                    if (state.article(id) == null) {
                        MissingArticleScreen(onBack = { navController.popBackStack() })
                    } else {
                        ArticleDetailScreen(
                            vm = vm,
                            articleId = id,
                            onBack = { navController.popBackStack() },
                            onOpenAi = { navController.navigate(Routes.ai(id)) },
                            onEdit = { navController.navigate(Routes.edit(id)) },
                            initialParagraph = entry.arguments?.getInt(Routes.ARG_PARAGRAPH) ?: -1
                        )
                    }
                }

                composable(
                    route = Routes.AI,
                    arguments = listOf(navArgument(Routes.ARG_ARTICLE_ID) { type = NavType.StringType })
                ) { entry ->
                    val id = entry.arguments?.getString(Routes.ARG_ARTICLE_ID).orEmpty()
                    AiReportScreen(
                        vm = vm,
                        articleId = id,
                        onBack = { navController.popBackStack() },
                        onOpenArticle = { navController.navigate(Routes.article(id)) },
                        onJump = { paragraph ->
                            navController.navigate(Routes.article(id, paragraph)) { popUpTo(Routes.HOME) }
                        },
                        onOpenMaterials = { navController.navigate(Routes.MATERIALS) }
                    )
                }

                composable(
                    route = Routes.EDIT,
                    arguments = listOf(navArgument(Routes.ARG_ARTICLE_ID) { type = NavType.StringType })
                ) { entry ->
                    val id = entry.arguments?.getString(Routes.ARG_ARTICLE_ID).orEmpty()
                    EditScreen(
                        vm = vm,
                        articleId = id,
                        onBack = { navController.popBackStack() },
                        onSent = {
                            navController.navigate(Routes.article(id)) {
                                popUpTo(Routes.HOME)
                            }
                        }
                    )
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth())
        }
    }
}

