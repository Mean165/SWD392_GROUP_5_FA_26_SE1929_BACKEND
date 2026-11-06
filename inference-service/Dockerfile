FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    BIND_HOST=127.0.0.1 \
    PORT=8000 \
    MODELS_DIR=/models \
    HF_HOME=/models/hf-cache

WORKDIR /srv/inference
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 libsndfile1 ffmpeg ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt /tmp/requirements.txt
RUN python -m pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch==2.14.1+cpu \
    && python -m pip install --no-cache-dir -r /tmp/requirements.txt
COPY app ./app
COPY data ./data
EXPOSE 8000
CMD ["sh", "-c", "exec python -m uvicorn app.main:app --host \"$BIND_HOST\" --port \"$PORT\""]
