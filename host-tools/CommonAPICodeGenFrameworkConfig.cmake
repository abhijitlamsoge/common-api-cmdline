
set(LOCAL_MAVEN_REPOSITORY_LOCATION ${CMAKE_CURRENT_BINARY_DIR}/MavenRepo)

set(MAVEN_REPOSITORY_INSTALLATION_LOCATION include/MavenRepositories)

if(NOT DEFINED MAVEN_REPOSITORIES_LOCATION)
	set(SOURCE_SHARED_MAVEN_REPOSITORIES_LOCATION ${CMAKE_INSTALL_PREFIX}/${MAVEN_REPOSITORY_INSTALLATION_LOCATION})
else()
	set(SOURCE_SHARED_MAVEN_REPOSITORIES_LOCATION ${MAVEN_REPOSITORIES_LOCATION})
endif()

message("Maven repository from other packages located in : ${SOURCE_SHARED_MAVEN_REPOSITORIES_LOCATION}")

set(MAVEN_OPTIONS "-Dmaven.repo.local=${LOCAL_MAVEN_REPOSITORY_LOCATION}")

set(COMMON_API_CODEGEN_JAR_DESTINATION_PATH share/CommonAPICodeGen)

macro(import_maven_repository PACKAGE_NAME)
    execute_process(
    	COMMAND cp -r ${SOURCE_SHARED_MAVEN_REPOSITORIES_LOCATION}/${PACKAGE_NAME} ${LOCAL_MAVEN_REPOSITORY_LOCATION}
    )
endmacro()

macro(export_maven_repository PACKAGE_NAME)
    install(DIRECTORY ${LOCAL_MAVEN_REPOSITORY_LOCATION}/ DESTINATION ${MAVEN_REPOSITORY_INSTALLATION_LOCATION}/${PACKAGE_NAME})
endmacro()

macro(add_maven_package PACKAGE_NAME PACKAGE_PATH DEPENDENCIES)
	add_custom_target(
		${PACKAGE_NAME} ALL
		COMMAND mvn ${MAVEN_OPTIONS} -f ${PACKAGE_PATH} install
		DEPENDS ${DEPENDENCIES}
	)
endmacro()
