#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <assert.h>

void error(char *msg) {
    perror(msg);
    exit(1);
}

int main(int argc, char *argv[]) {
    if (argc < 2) {
        fprintf(stderr,"usage: %s <port_du_serveur>\n", argv[0]);
        exit(1);
    }

    int sockfd, portno, n;
    struct sockaddr_in serv_addr;
    char buffer[256];

    portno = atoi(argv[1]);
    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) error("ERROR opening socket");

    memset((char *) &serv_addr, 0, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr.s_addr); // Connexion locale
    serv_addr.sin_port = htons(portno);

    printf("1. Tentative de connexion au serveur sur le port %d...\n", portno);
    if (connect(sockfd, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0)
        error("ERROR connecting (Le serveur est-il allumé ?)");

    char *test_msg = "announce listen 6881 seed [ video.mp4 2048 1024 hash1234 ] leech [ ]\n";
    printf("2. Envoi du message test : %s", test_msg);
    
    n = write(sockfd, test_msg, strlen(test_msg));
    if (n < 0) error("ERROR writing to socket");

    memset(buffer, 0, 256);
    n = read(sockfd, buffer, 255);
    if (n < 0) error("ERROR reading from socket");

    printf("3. Réponse reçue du serveur : %s", buffer);

    assert(strstr(buffer, "OK Announce saved. Port=6881, Seeds=1") != NULL);

    printf("\n✅\n");

    close(sockfd);
    return 0;
}