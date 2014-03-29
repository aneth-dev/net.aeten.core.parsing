SOURCE_VERSION = 1.7
JFLAGS ?= -g:source,lines,vars -encoding utf8
PROCESSOR_FACTORIES_MODULES ?= net.aeten.core
TOUCH_DIR = .touch


all: compile jar eclipse src test

# Sources
SRC = parsing.properties parsing.xml parsing.yaml
src: $(SRC)
parsing.properties:: aeten.core slf4j
parsing.xml::        aeten.core
parsing.yaml::       aeten.core

# COTS
COTS = aeten.core jcip.annotations slf4j
cots: $(COTS)
aeten.core::       jcip.annotations slf4j
jcip.annotations::
slf4j::

# Tests
TEST = parsing.test
test: $(TEST)
parsing.test:: aeten.core parsing.properties parsing.xml parsing.yaml slf4j.simple

# Tests COTS
TEST_COTS = slf4j.simple
slf4j.simple::     slf4j

clean:
	$(RM) -rf $(BUILD_DIR) $(DIST_DIR) $(GENERATED_DIR) $(TOUCH_DIR)

SRC_DIRS = src/ test/
MODULES = $(SRC) $(COTS) $(TEST) $(TEST_COTS)
include Java-make/java.mk

