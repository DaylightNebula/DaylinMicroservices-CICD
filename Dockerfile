FROM openjdk:19
COPY DaylinMicroservices-CICD-Builder/build/libs/DaylinMicroservices-CICD-Builder-all.jar DaylinMicroservices-CICD-Builder.jar
CMD ["java","-jar","DaylinMicroservices-CICD-Builder.jar"]