#include <stdio.h>
#include <stdlib.h>
#include <strings.h>
#include <unistd.h>
#include <sys/types.h> 
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <string.h>
#define BUFLEN 256
#include "message.h"

void error(char *msg)
{
    perror(msg);
    exit(1);
}

int main(int argc, char *argv[])
{
    int sockfd, newsockfd, portno;
    socklen_t cli_socket_len;
    char buffer[BUFLEN];
    struct sockaddr_in serv_addr, cli_addr;
    int n;
    if (argc < 2) {
        fprintf(stderr,"usage: %s <port>\n", argv[0]);
        exit(1);
    }
    sockfd = socket(AF_INET, SOCK_STREAM, 0);

    if (sockfd < 0) error("ERROR opening socket");

    bzero((char *) &serv_addr, sizeof(serv_addr));

    portno = atoi(argv[1]);

    serv_addr.sin_family = AF_INET;
    //serv_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    inet_pton(AF_INET, "0.0.0.0", &serv_addr.sin_addr.s_addr);// On a remplacer 127.0.0.1 par 0.0.0.0 pour que le serveur puisse accepter les connexions de n'importe quelle adresse IP
    serv_addr.sin_port = htons(portno);

    if (bind(sockfd, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0) error("ERROR on bind()");

    printf("=> listen() ? ...");
    getchar(); 
    printf("OK\n");

    listen(sockfd, 5);

    printf("=> accept() ? ...");
    getchar(); 
    printf("OK\n");

    cli_socket_len = sizeof(cli_addr);
    newsockfd = accept(sockfd, (struct sockaddr *) &cli_addr, &cli_socket_len);
    if (newsockfd < 0) error("ERROR on accept()");

    bzero(buffer, BUFLEN);

    printf("=> read() ? ...");
    getchar(); 
    printf("OK\n");

    n = read(newsockfd, buffer, BUFLEN); //on a modifié la taille de buffer lu(10)
    if (n < 0) error("ERROR reading from socket");
    printf("Here is the message: (size: %d bytes) %s\n", n, buffer);

    printf("=> write() ? ...");
    getchar(); 
    printf("OK\n");

    n = write(newsockfd, "I got your message",18);
    if (n < 0) error("ERROR writing to socket");

    MessageP2P *msg = add_message(buffer);
    printf("Commande: %s\n", msg->commande);
    printf("Port: %d\n", msg->port);
    return 0; 
}
