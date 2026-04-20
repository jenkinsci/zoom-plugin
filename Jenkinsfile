/*
 See the documentation for more options:
 https://github.com/jenkins-infra/pipeline-library/
*/
buildPlugin(
  // we use Docker for containerized tests
  useContainerAgent: true,
  configurations: [
    [platform: 'linux', jdk: 25],
    [platform: 'windows', jdk: 21],
])
