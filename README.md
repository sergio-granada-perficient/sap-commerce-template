# SAP Commerce Template

# Project Setup

## Requirements

Your SAP Commerce development environment should include the following:


1. **Java (JDK) 17** installed and configured in the system path. You can verity with `java --version`. 
2. SAP Commerce Suite version 2211.0 and SAP Commerce Cloud Integration Extension Pack 2211.0


## Install Java (JDK)
1. Go to [link](https://sap.github.io/SapMachine/)
2. Download JDK Sap Machine version 17
3. Execute the downloaded file and follow installation steps.
4. Add JAVA_HOME environment variable


## Project Installation
1. Make sure you are in the right GIT branch
2. Move the downloaded Sap Commerce Suite and Cloud Integration Extension Pack zip files into the dependencies folder using the correct file names: hybris-commerce-suite-2211.0.zip and hybris-commerce-integrations-2211.0.zip 
3. Go to the core-customize folder and setup your local environment by running the following command: ```./gradlew setupEnvironment```
4. Make sure the previous command executed successfully and then run: ```./gradlew yclean yall```
5. Go to hybris/bin/platform and set up ant environment variables by running: ```. ./setantenv.sh```
6. Initialize the system by running: ```ant initialize``` and make sure there are no errors.
7. Start you Sap Commerce instance from the platform folder by running: ```./hybrisserver.sh```

## Code Formatting
1. Import file dev-tools/intellij/hybrisJavaCodeconventions.xml in intellij, going to preferences -> editor -> code style -> Java and import scheme
