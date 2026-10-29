<img align="right" width="250" height="47" src="media/Gematik_Logo_Flag.png"/> <br/> 
 
# Release Notes Surveillance-Pseudonym-Service

## Release 1.2.2
- Updated minor/patch versions of dependencies
- Requests with pseudonyms belonging to different chains now return status code 422 instead of 500

## Release 1.2.1
- added prometheus pod annotations to purger helm chart
- optimized custom environment variables handling in helm chart
- updated docker base image to gematik1/osadl-alpine-openjdk25-jre:1.0.7
- arranged jvm options and resource limits
- fixed handling of falsy custom environment variables (false, 0) in helm chart
- added vex-documents to repository
- updated spring-parent to 4.1.11
- updated minor/patch versions of dependencies
- removed FF: FEATURE_FLAG_INDIVIDUAL_PSEUDONYM
- updated request cpu-limit from 1 CPU to 0,1 CPU
- Updated minor/patch versions of dependencies

## Release 1.2.0
- updated base-image and updated from java 21 to java 25
- Removed istio helm chart
- upgraded to spring boot 4

## Release 1.0.1
- fixed job annotations in helmchart

## Release 1.0.0

### added
- Rest Service to determine new pseudonym
- persistent pseudonym chain
- identical pseudonyms for the same person will be rejected
- persistent periods (logic to break down pseudonym chains)
- update pipelines and helm-charts for purger-module
- Purger application to delete database records past their retention period

### changed
- License updated to EUPL 1.2
- Dependencies updated