FROM debian:13-slim AS tools
ARG PLATFORM_TOOLS_VERSION=36.0.2
RUN test "$(dpkg --print-architecture)" = amd64 \
    && apt-get update && apt-get install -y --no-install-recommends ca-certificates curl unzip \
    && curl -fsSL "https://dl.google.com/android/repository/platform-tools_r${PLATFORM_TOOLS_VERSION}-linux.zip" -o /tmp/tools.zip \
    && unzip -j /tmp/tools.zip platform-tools/adb -d /usr/local/bin \
    && unzip -p /tmp/tools.zip platform-tools/NOTICE.txt > /tmp/NOTICE.platform-tools.txt

FROM debian:13-slim
COPY --from=tools /usr/local/bin/adb /usr/local/bin/adb
COPY --from=tools /tmp/NOTICE.platform-tools.txt /usr/share/doc/adb-gateway/NOTICE.platform-tools.txt
COPY reconnect.sh /usr/local/bin/adb-gateway-reconnect
ENV ADB_SERVER_PORT=5038
HEALTHCHECK --interval=15s --timeout=10s \
    CMD ["/bin/sh", "/usr/local/bin/adb-gateway-reconnect"]
# ADB's SIGINT handler shuts down the foreground server cleanly.
STOPSIGNAL SIGINT
ENTRYPOINT ["/bin/sh", "-c", "exec adb -a -P \"$ADB_SERVER_PORT\" server nodaemon"]
