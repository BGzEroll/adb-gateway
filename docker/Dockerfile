FROM debian:13-slim AS tools
ARG PLATFORM_TOOLS_VERSION=37.0.3
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl unzip \
    && curl -fsSL "https://dl.google.com/android/repository/platform-tools_r${PLATFORM_TOOLS_VERSION}-linux.zip" -o /tmp/tools.zip \
    && unzip -j /tmp/tools.zip platform-tools/adb -d /usr/local/bin

FROM debian:13-slim
RUN apt-get update && apt-get install -y --no-install-recommends python3 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=tools /usr/local/bin/adb /usr/local/bin/adb
WORKDIR /app
COPY adbgw-server.py /app/adbgw-server.py
ENV POLL=5 STATUS_PORT=8770
HEALTHCHECK --interval=30s --timeout=6s --start-period=10s \
  CMD python3 -c "import os,urllib.request; urllib.request.urlopen('http://127.0.0.1:'+os.environ['STATUS_PORT']+'/status',timeout=5)"
CMD ["python3", "/app/adbgw-server.py"]
