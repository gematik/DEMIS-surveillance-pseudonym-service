<img align="right" width="250" height="47" src="media/Gematik_Logo_Flag.png"/> <br/> 

# Surveillance-Pseudonym-Service

<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About The Project</a>
      <ul>
        <li><a href="#quality-gate">Quality Gates</a></li>
        <li><a href="#release-notes">Release Notes</a></li>
      </ul>
    </li>
    <li>
      <a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#prerequisites">Prerequisites</a></li>
        <li><a href="#installation">Installation</a></li>
      </ul>
    </li>
    <li><a href="#usage">Usage</a></li>
    <li><a href="#security-policy">Security Policy</a></li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
  </ol>
</details>

## About The Project

Surveillance pseudonym service is a general service for pseudonymization in surveillance programs. Each surveillance
program has its own instance and configuration of the service. Pseudonymization in surveillance programs has to meet a 
couple of different requirements. It has to support the aggregation of patient data collected in a specific time period 
for analysis. This includes retrospectively transmitted data from observations in the past. However, RKI should not be 
able to associate data with a particular patient for longer than a predefined period of time. Further, the pseudonym of 
a patient has to be different in each surveillance program.

### Quality Gates

[![Quality Gate Status](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Asurveillance-pseudonym-service&metric=alert_status&token=sqb_4136b60d23e04e72155d90b4249241179b16c58f)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Asurveillance-pseudonym-service)
[![Vulnerabilities](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Asurveillance-pseudonym-service&metric=vulnerabilities&token=sqb_4136b60d23e04e72155d90b4249241179b16c58f)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Asurveillance-pseudonym-service)
[![Bugs](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Asurveillance-pseudonym-service&metric=bugs&token=sqb_4136b60d23e04e72155d90b4249241179b16c58f)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Asurveillance-pseudonym-service)
[![Code Smells](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Asurveillance-pseudonym-service&metric=code_smells&token=sqb_4136b60d23e04e72155d90b4249241179b16c58f)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Asurveillance-pseudonym-service)
[![Lines of Code](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Asurveillance-pseudonym-service&metric=ncloc&token=sqb_4136b60d23e04e72155d90b4249241179b16c58f)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Asurveillance-pseudonym-service)
[![Coverage](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Asurveillance-pseudonym-service&metric=coverage&token=sqb_4136b60d23e04e72155d90b4249241179b16c58f)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Asurveillance-pseudonym-service)

### Release Notes

See [ReleaseNotes.md](./ReleaseNotes.md) for all information regarding the (newest) releases.

## Getting Started

### Prerequisites

The Project requires Java 21 and Maven 3.8+.

### Installation

The Project can be built with the following command:

```sh
mvn clean install
```

The Docker Image associated to the service can be built with the extra profile `docker`:

```sh
mvn clean install -Pdocker
```

## Usage

The application can be executed from a JAR file or a Docker Image:

```sh
# As JAR Application
java -jar target/surveillance-pseudonym-service.jar
# As Docker Image
docker run --rm -it -p 8080:8080 surveillance-pseudonym-service:latest
```

It can also be deployed on Kubernetes by using the Helm Chart defined in the folder `deployment/helm/surveillance-pseudonym-service`:

```ssh
helm install surveillance-pseudonym-service ./deployment/helm/surveillance-pseudonym-service
```

### Continuous Integration and Delivery

The project contains Jenkins Pipelines to perform automatic build and scanning (`ci.jenkinsfile`) and release (based on retagging of the given Git Tag, `release.jenkinsfile`).
Please adjust the variable values defined at the beginning of the pipelines!

For both the pipelines, you need to create a first initial Release Version in JIRA, so it can be retrieved from Jenkins with the Jenkins Shared Library functions.

**BEWARE**: The Release Pipeline requires a manual configuration of the parameters over the Jenkins UI, defining a JIRA Release Version plugin and naming it `JIRA_RELEASE_VERSION`.
The Information such as Project Key and Regular Expression depends on the project and must be correctly configured.

### Endpoints

| HTTP Method | Endpoint        | Parameters                                  | Body | Returns                       | Description                                                                                               |
|-------------|-----------------|---------------------------------------------|------|-------------------------------|-----------------------------------------------------------------------------------------------------------|
| POST        | /pseudonym      | - pseudonym1<br>- pseudonym2<br>- date      |      | - code system<br>- value      | - Will determine the pseudonym of a patient that is used in the notifications which is routed to RKI.     |

## Implementation Notes

### Concurrency 

It’s possible for concurrent requests to arrive for the same patient (i.e., identical incoming pseudonyms) but with different reference dates. Without coordination, this could lead to two adjacent periods being created side by side when a single merged period would have sufficed. To prevent this, we use **pessimistic locking**.

- The lock is scoped to a “chain” (patient-level grouping), so different patients can still be processed in parallel.
- To lock a chain exclusively, we maintain one database row per chain in the Chain table.

#### How it works
1. Chain presence and creation
   - If both pseudonyms are not yet known, the ChainService creates a new Chain entry transactionally, together with the corresponding PseudonymChain entries.
   - Chain-related tables (pseudonym_chain, period) have a foreign key to Chain, guaranteeing that any entry is always associated with an existing chain.
   - This guarantees there is always exactly one row to lock for a given chain.
2. Exclusive lock acquisition
   - The PeriodService begins by locking the chain’s row for the entire duration of its transaction using SELECT ... FOR UPDATE.
   - This ensures that any other thread targeting the same chain will block at this point until the lock holder completes its period logic.
3. Consistent period updates
   - Only after obtaining the exclusive lock, the PeriodService reads the existing period records within the same transaction.
   - Based on those records, it either inserts a new period or amends an existing one, ensuring no overlapping or needlessly adjacent periods are created.

## Security Policy
If you want to see the security policy, please check our [SECURITY.md](.github/SECURITY.md).

## Contributing
If you want to contribute, please check our [CONTRIBUTING.md](.github/CONTRIBUTING.md).

## License

Copyright 2025 gematik GmbH

EUROPEAN UNION PUBLIC LICENCE v. 1.2

EUPL © the European Union 2007, 2016

See the [LICENSE](./LICENSE.md) for the specific language governing permissions and limitations under the License

## Additional Notes and Disclaimer from gematik GmbH

1. Copyright notice: Each published work result is accompanied by an explicit statement of the license conditions for use. These are regularly typical conditions in connection with open source or free software. Programs described/provided/linked here are free software, unless otherwise stated.
2. Permission notice: Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
   1. The copyright notice (Item 1) and the permission notice (Item 2) shall be included in all copies or substantial portions of the Software.
   2. The software is provided "as is" without warranty of any kind, either express or implied, including, but not limited to, the warranties of fitness for a particular purpose, merchantability, and/or non-infringement. The authors or copyright holders shall not be liable in any manner whatsoever for any damages or other claims arising from, out of or in connection with the software or the use or other dealings with the software, whether in an action of contract, tort, or otherwise.
   3. We take open source license compliance very seriously. We are always striving to achieve compliance at all times and to improve our processes. If you find any issues or have any suggestions or comments, or if you see any other ways in which we can improve, please reach out to: ospo@gematik.de
3. Please note: Parts of this code may have been generated using AI-supported technology. Please take this into account, especially when troubleshooting, for security analyses and possible adjustments.

## Contact
E-Mail to [DEMIS Entwicklung](mailto:demis-entwicklung@gematik.de?subject=[GitHub]%20Surveillance-Pseudonym-Service)