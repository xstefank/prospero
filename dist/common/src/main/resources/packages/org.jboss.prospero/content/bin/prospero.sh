#!/bin/sh

DIRNAME=`dirname "$0"`
GREP="grep"

. "$DIRNAME/common.sh"

# OS specific support (must be 'true' or 'false').
cygwin=false;
darwin=false;
case "`uname`" in
    CYGWIN*)
        cygwin=true
        ;;

    Darwin*)
        darwin=true
        ;;
esac

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
    [ -n "$PROSPERO_HOME" ] &&
        PROSPERO_HOME=`cygpath --unix "$PROSPERO_HOME"`
    [ -n "$JAVA_HOME" ] &&
        JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
    [ -n "$JAVAC_JAR" ] &&
        JAVAC_JAR=`cygpath --unix "$JAVAC_JAR"`
fi

# Setup PROSPERO_HOME
RESOLVED_PROSPERO_HOME=`cd "$DIRNAME/.."; pwd`
if [ "x$PROSPERO_HOME" = "x" ]; then
    # get the full path (without any relative bits)
    PROSPERO_HOME=$RESOLVED_PROSPERO_HOME
else
 SANITIZED_PROSPERO_HOME=`cd "$PROSPERO_HOME"; pwd`
 if [ "$RESOLVED_PROSPERO_HOME" != "$SANITIZED_PROSPERO_HOME" ]; then
   echo "WARNING PROSPERO_HOME may be pointing to a different installation - unpredictable results may occur."
   echo ""
 fi
fi
export PROSPERO_HOME

if [ "x$JBOSS_MODULEPATH" = "x" ]; then
    JBOSS_MODULEPATH="$PROSPERO_HOME/modules"
fi

# Setup the JVM
if [ "x$JAVA" = "x" ]; then
    if [ "x$JAVA_HOME" != "x" ]; then
        JAVA="$JAVA_HOME/bin/java"
    else
        JAVA="java"
    fi
fi

# Set default modular JVM options
setDefaultModularJvmOptions $JAVA_OPTS
JAVA_OPTS="$JAVA_OPTS $DEFAULT_MODULAR_JVM_OPTIONS"

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
    PROSPERO_HOME=`cygpath --path --windows "$PROSPERO_HOME"`
    JAVA_HOME=`cygpath --path --windows "$JAVA_HOME"`
    JBOSS_MODULEPATH=`cygpath --path --windows "$JBOSS_MODULEPATH"`
fi

if $darwin ; then
    # Add the apple gui packages for the gui client
    JAVA_OPTS="$JAVA_OPTS -Djboss.modules.system.pkgs=com.apple.laf,com.apple.laf.resources"
else
    # Add base package for L&F
    JAVA_OPTS="$JAVA_OPTS -Djboss.modules.system.pkgs=com.sun.java.swing"
fi

# Override ibm JRE behavior
JAVA_OPTS="$JAVA_OPTS -Dcom.ibm.jsse2.overrideDefaultTLS=true"

# Set default log location
LOG_FILE_CONF=`echo $JAVA_OPTS | grep "org.wildfly.prospero.log.file"`
if [ "x$LOG_FILE_CONF" = "x" ]; then
  JAVA_OPTS="$JAVA_OPTS -Dorg.wildfly.prospero.log.file=${PROSPERO_HOME}/logs/installation.log"
fi

# Sample JPDA settings for remote socket debugging
#JAVA_OPTS="$JAVA_OPTS -agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=y"

# WFCORE-5216 - evaluate any eventual env variables
JBOSS_MODULEPATH=$(eval echo \"${JBOSS_MODULEPATH}\")

LOG_CONF=`echo $JAVA_OPTS | grep "logging.configuration"`
if [ "x$LOG_CONF" = "x" ]; then
    exec "$JAVA" $JAVA_OPTS -Dlogging.configuration=file:"$PROSPERO_HOME"/bin/${prospero.dist.name}-logging.properties -jar "$PROSPERO_HOME"/jboss-modules.jar -mp "${JBOSS_MODULEPATH}" org.jboss.prospero "$@"
else
    exec "$JAVA" $JAVA_OPTS -jar "$PROSPERO_HOME"/jboss-modules.jar -mp "${JBOSS_MODULEPATH}" org.jboss.prospero "$@"
fi
