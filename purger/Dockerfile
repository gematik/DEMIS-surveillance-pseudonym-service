# Declare Source Digest for the Base Image
ARG SOURCE_DIGEST=9323904961b9adac73c36c9771a187ecea21111d96a331eec6173b9649481243
FROM gematik1/osadl-alpine-openjdk21-jre:1.0.8@sha256:${SOURCE_DIGEST}
# Redeclare Source Digest to be used in the build context
# https://docs.docker.com/engine/reference/builder/#understand-how-arg-and-from-interact
ARG SOURCE_DIGEST=9323904961b9adac73c36c9771a187ecea21111d96a331eec6173b9649481243

# The STOPSIGNAL instruction sets the system call signal that will be sent to the container to exit
# SIGTERM = 15 - https://de.wikipedia.org/wiki/Signal_(Unix)
STOPSIGNAL SIGTERM

# Define the exposed port or range of ports for the service
#EXPOSE 8080

HEALTHCHECK --interval=15s \
            --timeout=5s \
            --start-period=5s \
            --retries=3 \
            CMD ["/bin/pidof", "java"]

# Default USERID and GROUPID
ARG USERID=10000
ARG GROUPID=10000

COPY --chown=$USERID:$GROUPID target/surveillance-pseudonym-purger.jar /app.jar

# Run as User (not root)
USER $USERID:$USERID

ENTRYPOINT ["java", "-jar", "/app.jar"]

# Git Args
ARG COMMIT_HASH
ARG VERSION

###########################
# Labels
###########################
LABEL de.gematik.vendor="gematik GmbH" \
      maintainer="software-development@gematik.de" \
      de.gematik.app="DEMIS Surveillance Pseudonym Purger" \
      de.gematik.git-repo-name="https://gitlab.prod.ccs.gematik.solutions/demis/services/surveillance-pseudonym-service.git" \
      de.gematik.commit-sha=$COMMIT_HASH \
      de.gematik.version=$VERSION \
      de.gematik.source.digest=$SOURCE_DIGEST
