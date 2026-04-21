/*
REDACTION DE CES TESTS PAR IA
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>
#include <time.h>
#include <sys/time.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#define BUF_SIZE 256

typedef struct {
    int id;
    int port;
    int ok;
    char err[128];
} ThreadArg;

static void sleep_ms(long ms) {
    struct timespec ts;
    ts.tv_sec = ms / 1000;
    ts.tv_nsec = (ms % 1000) * 1000000L;
    nanosleep(&ts, NULL);
}

static int connect_to_tracker(int port) {
    int sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) {
        return -1;
    }

    struct timeval timeout;
    timeout.tv_sec = 1;
    timeout.tv_usec = 0;
    setsockopt(sockfd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));

    struct sockaddr_in serv_addr;
    memset(&serv_addr, 0, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr.s_addr);
    serv_addr.sin_port = htons(port);

    if (connect(sockfd, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        close(sockfd);
        return -1;
    }

    return sockfd;
}

static int send_and_recv(int sockfd, const char *msg, char *buffer, size_t buffer_size) {
    ssize_t n;

    memset(buffer, 0, buffer_size);
    if (write(sockfd, msg, strlen(msg)) < 0) {
        return -1;
    }

    n = read(sockfd, buffer, buffer_size - 1);
    if (n <= 0) {
        return -1;
    }

    buffer[n] = '\0';
    return 0;
}

static void *client_thread(void *arg) {
    ThreadArg *ctx = (ThreadArg *)arg;
    char buffer[BUF_SIZE];
    int sockfd = connect_to_tracker(ctx->port);

    ctx->ok = 0;
    ctx->err[0] = '\0';

    if (sockfd < 0) {
        strcpy(ctx->err, "connexion impossible");
        return NULL;
    }

    if (ctx->id == 1) {
        // Client 1: reste inactif un moment (ancien serveur bloquait ici)
        sleep_ms(2000);
        if (send_and_recv(sockfd, "look [ demo ]\n", buffer, sizeof(buffer)) != 0) {
            strcpy(ctx->err, "client 1: pas de reponse apres attente");
            close(sockfd);
            return NULL;
        }
        if (strstr(buffer, "list [") == NULL) {
            strcpy(ctx->err, "client 1: reponse invalide");
            close(sockfd);
            return NULL;
        }
    }

    if (ctx->id == 2) {
        sleep_ms(100);
        if (send_and_recv(sockfd, "announce listen 7002 seed [ mfile.dat 1000 100 mhash001 ] leech [ ]\n", buffer, sizeof(buffer)) != 0) {
            strcpy(ctx->err, "client 2: announce sans reponse");
            close(sockfd);
            return NULL;
        }
        if (strcmp(buffer, "ok\n") != 0) {
            strcpy(ctx->err, "client 2: mauvaise reponse announce");
            close(sockfd);
            return NULL;
        }
    }

    if (ctx->id == 3) {
        sleep_ms(200);
        if (send_and_recv(sockfd, "badcommand\n", buffer, sizeof(buffer)) != 0) {
            strcpy(ctx->err, "client 3: commande en parallele sans reponse");
            close(sockfd);
            return NULL;
        }
        if (strcmp(buffer, "ERROR Unknown command\n") != 0) {
            strcpy(ctx->err, "client 3: mauvaise reponse erreur");
            close(sockfd);
            return NULL;
        }
    }

    ctx->ok = 1;
    close(sockfd);
    return NULL;
}

int main(int argc, char *argv[]) {
    if (argc < 2) {
        return 1;
    }

    int port = atoi(argv[1]);
    pthread_t threads[3];
    ThreadArg args[3];

    for (int i = 0; i < 3; i++) {
        args[i].id = i + 1;
        args[i].port = port;
        args[i].ok = 0;
        args[i].err[0] = '\0';
        if (pthread_create(&threads[i], NULL, client_thread, &args[i]) != 0) {
            return 1;
        }
    }

    for (int i = 0; i < 3; i++) {
        pthread_join(threads[i], NULL);
    }

    for (int i = 0; i < 3; i++) {
        if (!args[i].ok) {
            fprintf(stderr, "Echec thread client %d: %s\n", args[i].id, args[i].err);
            return 1;
        }
    }

    printf("✅ TEST ACCEPTATION MULTI-CLIENTS REUSSI (3 clients en parallele)\n\n");
    return 0;
}
