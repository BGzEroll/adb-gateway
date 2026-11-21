FROM debian:13-slim
RUN apt-get update && apt-get install -y --no-install-recommends \
        android-tools-adb python3 ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY adbgw-server.py /app/adbgw-server.py
ENV POLL=20 FIXED=5555 STATUS_PORT=8770
EXPOSE 8770
HEALTHCHECK --interval=30s --timeout=6s --start-period=10s \
  CMD python3 -c "import urllib.request;urllib.request.urlopen('http://127.0.0.1:8770/status',timeout=5)"
CMD ["python3", "/app/adbgw-server.py"]