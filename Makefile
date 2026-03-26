CC := gcc
CFLAGS := -Wall -Wextra -std=c11 -g -Isrc/tracker

JAVAC := javac
JAVA := java

BIN_DIR := bin
TRACKER_BIN := $(BIN_DIR)/tracker_server
TEST_BIN := $(BIN_DIR)/test_message
TEST_SERVER_BIN := $(BIN_DIR)/test_server
PEER_OUT_DIR := $(BIN_DIR)/peer
PEER_STAMP := $(PEER_OUT_DIR)/.compiled
PEER_SOURCES := $(wildcard src/peer/*.java)

TRACKER_PORT ?= 8080
TEST_PORT ?= 8081

.PHONY: all tracker peer tests test run-tests run-tracker run-server clean help

all: tracker peer tests

help:
	@echo "Targets disponibles :"
	@echo "  make all          -> compile tracker + peer + tests"
	@echo "  make tracker      -> compile le serveur tracker (C)"
	@echo "  make peer         -> compile les classes peer (Java)"
	@echo "  make tests        -> compile les tests C (parsing + serveur)"
	@echo "  make run-tests    -> exécute tous les tests (unitaires et intégration)"
	@echo "  make test         -> compile puis exécute les tests"
	@echo "  make run-tracker  -> lance le serveur tracker"
	@echo "  make run-server   -> alias de run-tracker"
	@echo "  make clean        -> supprime les artefacts de build"
	@echo ""
	@echo "Option :"
	@echo "  TRACKER_PORT=xxxx (défaut: 8080)"

$(BIN_DIR):
	mkdir -p $(BIN_DIR)

$(PEER_OUT_DIR):
	mkdir -p $(PEER_OUT_DIR)

tracker: $(TRACKER_BIN)

$(TRACKER_BIN): src/tracker/server.c src/tracker/message.c src/tracker/message.h | $(BIN_DIR)
	$(CC) $(CFLAGS) src/tracker/server.c src/tracker/message.c -o $(TRACKER_BIN)

tests: $(TEST_BIN) $(TEST_SERVER_BIN)

$(TEST_BIN): test/test_message.c src/tracker/message.c src/tracker/message.h | $(BIN_DIR)
	$(CC) $(CFLAGS) test/test_message.c src/tracker/message.c -o $(TEST_BIN)

# Nouvelle règle pour compiler le test du serveur
$(TEST_SERVER_BIN): test/test_server.c | $(BIN_DIR)
	$(CC) $(CFLAGS) test/test_server.c -o $(TEST_SERVER_BIN)

run-tests: $(TEST_BIN) $(TEST_SERVER_BIN) $(TRACKER_BIN)
	@echo "\n=== 1. TESTS UNITAIRES (Parsing) ==="
	./$(TEST_BIN)
	
	@echo "\n=== 2. TESTS D'INTÉGRATION (Serveur) ==="
	@./$(TRACKER_BIN) $(TEST_PORT) & SERVER_PID=$$!; \
	sleep 1; \
	./$(TEST_SERVER_BIN) $(TEST_PORT); \
	TEST_RESULT=$$?; \
	exit $$TEST_RESULT

test: tests run-tests

peer: $(PEER_STAMP)

$(PEER_STAMP): $(PEER_SOURCES) | $(PEER_OUT_DIR)
	$(JAVAC) -d $(PEER_OUT_DIR) $(PEER_SOURCES)
	touch $(PEER_STAMP)

run-tracker: $(TRACKER_BIN)
	$(TRACKER_BIN) $(TRACKER_PORT)

run-server: run-tracker

clean:
	rm -f $(TRACKER_BIN) $(TEST_BIN) $(TEST_SERVER_BIN)
	rm -rf $(PEER_OUT_DIR)