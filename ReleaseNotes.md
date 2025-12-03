<img align="right" width="250" height="47" src="media/Gematik_Logo_Flag.png"/> <br/> 
 
# Release Notes Surveillance-Pseudonym-Service

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
