from fastapi import FastAPI
from fastapi.responses import JSONResponse

app = FastAPI()

@app.get('/health')
async def health():
    return JSONResponse({"status": "ok", "service": "DevOpsTest"})