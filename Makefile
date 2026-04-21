CC := gcc
CFLAGS := -Wall -Wextra -std=c11 -g -Isrc/tracker

JAVAC := javac
JAVA := java

BIN_DIR := bin
TRACKER_BIN := $(BIN_DIR)/tracker_server
TEST_BIN := $(BIN_DIR)/test_message
TEST_SERVER_BIN := $(BIN_DIR)/test_server
TEST_FILE_BIN := $(BIN_DIR)/test_file
TEST_CLIENT_BIN := $(BIN_DIR)/test_client
TEST_MULTI_CLIENTS_BIN := $(BIN_DIR)/test_multi_clients
PEER_OUT_DIR := $(BIN_DIR)/peer
PEER_STAMP := $(PEER_OUT_DIR)/.compiled
TEST_PEERS_BIN := $(BIN_DIR)/test_peers
PEER_SOURCES := $(wildcard src/peer/*.java)

TRACKER_PORT ?= 8080
TEST_PORT ?= 8081
TEST_MULTI_PORT ?= 8082

.PHONY: all tracker peer tests test run-tests run-tracker run-server stop-tracker clean help

all: $(TRACKER_BIN) $(TEST_BIN) $(TEST_SERVER_BIN) $(TEST_FILE_BIN) $(TEST_CLIENT_BIN) $(TEST_MULTI_CLIENTS_BIN) $(TEST_PEERS_BIN)

$(BIN_DIR):
	mkdir -p $(BIN_DIR)

$(PEER_OUT_DIR):
	mkdir -p $(PEER_OUT_DIR)

$(TRACKER_BIN): src/tracker/server.c src/tracker/message.c src/tracker/file.c src/tracker/peers.c src/tracker/client.c | $(BIN_DIR)
	$(CC) $(CFLAGS) $^ -o $@

$(TEST_BIN): test/test_message.c src/tracker/message.c src/tracker/file.c src/tracker/peers.c | $(BIN_DIR)
	$(CC) $(CFLAGS) $^ -o $@

$(TEST_SERVER_BIN): test/test_server.c | $(BIN_DIR)
	$(CC) $(CFLAGS) $^ -o $@

$(TEST_FILE_BIN): test/test_file.c src/tracker/file.c src/tracker/peers.c | $(BIN_DIR)
	$(CC) $(CFLAGS) $^ -o $@

$(TEST_CLIENT_BIN): test/test_client.c src/tracker/client.c | $(BIN_DIR)
	$(CC) $(CFLAGS) $^ -o $@

$(TEST_MULTI_CLIENTS_BIN): test/test_multi_clients.c | $(BIN_DIR)
	$(CC) $(CFLAGS) $^ -o $@ -pthread

$(TEST_PEERS_BIN): test/test_peers.c src/tracker/peers.c | $(BIN_DIR)
	$(CC) $(CFLAGS) $^ -o $@

run-tests: all
	@echo "\n=== 1. TESTS PEERS ==="
	./$(TEST_PEERS_BIN)
	@echo "\n=== 2. TESTS CLIENT ==="
	./$(TEST_CLIENT_BIN)
	@echo "\n=== 3. TESTS FILE ==="
	./$(TEST_FILE_BIN)
	@echo "\n=== 4. TESTS PARSING ==="
	./$(TEST_BIN)
	@echo "\n=== 5. TESTS D'INTÉGRATION SERVEUR ==="
	@./$(TRACKER_BIN) $(TEST_PORT) & SERVER_PID=$$!; \
	sleep 1; \
	./$(TEST_SERVER_BIN) $(TEST_PORT); \
	TEST_RESULT=$$?; \
	kill $$SERVER_PID; \
	if [ $$TEST_RESULT -ne 0 ]; then exit $$TEST_RESULT; fi
	@echo "\n=== 6. TEST ACCEPTATION MULTI-CLIENTS ==="
	@./$(TRACKER_BIN) $(TEST_MULTI_PORT) & SERVER_PID=$$!; \
	sleep 1; \
	./$(TEST_MULTI_CLIENTS_BIN) $(TEST_MULTI_PORT); \
	TEST_RESULT=$$?; \
	kill $$SERVER_PID; \
	exit $$TEST_RESULT

test: tests run-tests

peer: $(PEER_STAMP)

$(PEER_STAMP): $(PEER_SOURCES) | $(PEER_OUT_DIR)
	$(JAVAC) -d $(PEER_OUT_DIR) $(PEER_SOURCES)
	touch $(PEER_STAMP)

stop-tracker:
	@pkill -9 -x tracker_server >/dev/null 2>&1 || true
	@for i in 1 2 3 4 5 6 7 8 9 10; do \
		if ! ss -ltn "( sport = :$(TRACKER_PORT) )" | tail -n +2 | grep -q .; then \
			break; \
		fi; \
		sleep 0.1; \
	done

run-tracker: stop-tracker $(TRACKER_BIN)
	$(TRACKER_BIN) $(TRACKER_PORT)


run-server: run-tracker




clean:
	rm -rf $(BIN_DIR)