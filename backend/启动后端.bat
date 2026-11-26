@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

where python >nul 2>nul
if errorlevel 1 (
    echo [x] 没找到 python 命令。请先装 Python 3.10+，安装时勾选 "Add Python to PATH"。
    pause
    exit /b 1
)

python -c "import fastapi, uvicorn, docx, openai, multipart" >nul 2>nul
if errorlevel 1 (
    echo [i] 依赖还没装全，正在安装...
    python -m pip install -r requirements.txt
    if errorlevel 1 (
        echo [x] 依赖安装失败，请手动执行: python -m pip install -r requirements.txt
        pause
        exit /b 1
    )
)

if not exist ".env" (
    echo [i] 还没有 backend\.env，已从 .env.example 复制一份。
    copy /y ".env.example" ".env" >nul
    echo [i] 请把 DEEPSEEK_API_KEY 填进去，保存后关掉记事本。
    notepad ".env"
)

echo.
echo [ok] 后端启动中: http://127.0.0.1:8000    （接口文档 http://127.0.0.1:8000/docs）
echo [ok] 手机/模拟器访问时用本机局域网 IP，例如 http://192.168.x.x:8000
echo.
python -m uvicorn server.main:app --host 0.0.0.0 --port 8000
pause
