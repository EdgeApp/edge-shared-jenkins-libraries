class Constants {

    static final String STARTED = 'STARTED'
    static final String SUCCESS = 'SUCCESS'
    static final String UNSTABLE = 'UNSTABLE'
    static final String FAILURE = 'FAILURE'
    static final String BLACK = '#000000'
    static final Map SLACK_COLORS = [
      (this.STARTED): '#FFFFFF',
      (this.SUCCESS): 'good',
      (this.UNSTABLE): 'warning',
      (this.FAILURE): 'danger'
    ]
    static final String JENKINS_CHANNEL = 'jenkins-builds'
    static final String DEV_CHANNEL = 'dev'
    static final String DEV_INTERNAL_CHANNEL = 'dev-internal'
    static final String FROM = 'Jenkins'
    static final String TO = '$DEFAULT_RECIPIENTS'
    static final String TEMPLATE_FILE = 'groovy-html-gmail.template'
    static final String BODY = '${SCRIPT, template="groovy-html-gmail.template"}'
    static final String MIME_TYPE = 'text/html'

}

// Creating the email plugin's templates folder and template file in case it does not exists
void loadTemplate(String templateFileName) {
  String filePath = "${JENKINS_HOME}/email-templates/${templateFileName}"

  if (!fileExists(filePath)) {
    dir ("${JENKINS_HOME}/email-templates") {
      writeFile file: filePath, text: libraryResource("org/edge/templates/${templateFileName}")
    }
  }
}

// Send emails only in case the build failed
void emailNotifier(String buildStatus) {
  String statusMessage = "${buildStatus}: Build '${JOB_NAME} [${BUILD_NUMBER}]'"

  if (buildStatus == Constants.FAILURE) {
    emailext (
      from: Constants.FROM,
      to: Constants.TO,
      subject: statusMessage,
      mimeType: Constants.MIME_TYPE,
      body: Constants.BODY
    )
  }
}

// Send slack notifications to the jenkins channel and also to the dev/dev-internal in case the build has changed status
void slackNotifier(String currentBuildStatus, String previousBuildStatus) {
  // Build slack message
  String statusMessage = "${currentBuildStatus}: Build '${JOB_NAME} [${BUILD_NUMBER}]'"
  String versionMessage = "Build's version: ${currentBuild?.description?.toString() ?: 'Not assigned a version'}"
  String durationMessage = "Build's duration: ${currentBuild?.durationString?.toString() ?: '0'}"
  String linkMessage = "Build's link: ${BUILD_URL}"
  String buildInfoMessage = "\n${versionMessage}\n${durationMessage}\n${linkMessage}"

  // Removing `and counting` for the durationMessage if the build is done
  if (currentBuildStatus != Constants.STARTED) {
    durationMessage = durationMessage.replace(' and counting', '')
  }

  // Pick slack's message color
  String slackcolor = Constants.SLACK_COLORS[currentBuildStatus] ?: Constants.BLACK

  // Send slack notifications to the default jenkins channel
  slackSend (channel: Constants.JENKINS_CHANNEL, color: slackcolor, message: "${statusMessage}${buildInfoMessage}")

  // Notify the 'dev' slack channel if the build status has changed from 'FAILURE' to 'SUCCESS'
  if (previousBuildStatus == Constants.FAILURE && currentBuildStatus == Constants.SUCCESS) {
    String successMessage = "${statusMessage} has been fixed and is back to normal${buildInfoMessage}"
    slackSend (channel: Constants.DEV_CHANNEL, color: slackcolor, message: successMessage)
    slackSend (channel: Constants.DEV_INTERNAL_CHANNEL, color: slackcolor, message: successMessage)
  }
  // Notify the 'dev' slack channel if the build status has changed from 'SUCCESS' to 'FAILURE'
  if (previousBuildStatus == Constants.SUCCESS && currentBuildStatus == Constants.FAILURE) {
    String failureMessage = "${statusMessage} is broken and needs to be fixed${buildInfoMessage}"
    slackSend (channel: Constants.DEV_CHANNEL, color: slackcolor, message: failureMessage)
    slackSend (channel: Constants.DEV_INTERNAL_CHANNEL, color: slackcolor, message: failureMessage)
  }
}

/******************************************************/
/** Send notifications based on current build status **/
/******************************************************/
void call(currentBuild) {
  loadTemplate(Constants.TEMPLATE_FILE)

  // Build status of null means successful
  String buildStatus = currentBuild?.result?.toString() ?: Constants.STARTED
  String previousBuildStatus = currentBuild?.rawBuild?.getPreviousBuild()?.result?.toString() ?: Constants.SUCCESS

  slackNotifier(buildStatus, previousBuildStatus)
  emailNotifier(buildStatus)
}
