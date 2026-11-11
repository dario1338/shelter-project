# Jenkins controller, built from code: pinned base image, pinned plugins, configuration baked in.
#
# - Base image: the same LTS line that runs in production today.
# - Plugins: every plugin (including dependencies) at an exact version, see plugins.txt.
# - Configuration: JCasC files and the Job DSL script from ./casc (no secrets in them).
FROM jenkins/jenkins:2.580.2-lts-jdk21

# The setup wizard is not needed: the admin user comes from JCasC.
# The inbound agent port is closed in casc/00-system.yaml (slaveAgentPort: -1).
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"

# JCasC reads every *.yaml in this directory and merges them.
ENV CASC_JENKINS_CONFIG=/usr/share/jenkins/casc

# A ${VARIABLE} that cannot be resolved (env var or Docker secret) stops the start
# instead of silently becoming an empty string.
ENV CASC_STRICT_SECRET_RESOLUTION=true

COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

COPY --chown=jenkins:jenkins casc/ /usr/share/jenkins/casc/
