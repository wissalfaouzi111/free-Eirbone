#include <stdio.h>
#include <stdlib.h>
#include <strings.h>
#include <unistd.h>
#include <sys/types.h> 
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <string.h>
#include "message.h" 

// pour gerer le ctrl+C et liberer le port
#include <signal.h>  
int sockfd = -1;

void handle_sigint(int sig) {
    (void)sig;
    printf("\nArrêt : Ctrl+C détecté \n");
    if (sockfd >= 0) {
        close(sockfd);
    }
    exit(0);
}

void error(char *msg){
    perror(msg);
    exit(1);
}

void anwser(MessageP2P *msg, int newsockfd, int n) {
    char response[BUFLEN];
    bzero(response, BUFLEN);
    if (msg == NULL || msg->commande[0] == '\0') {
        sprintf(response, "ERROR Invalid format\n");
    } else if (strcmp(msg->commande, "announce") == 0) {
        sprintf(response, "OK Announce saved. Port=%d, Seeds=%d\n", msg->port, msg->nb_seed);
    } else if (strcmp(msg->commande, "look") == 0) {
        sprintf(response, "OK Look received\n");
    } else {
        sprintf(response, "ERROR Unknown command\n");
    }

    n = write(newsockfd, response, strlen(response));
    if (n < 0) error("ERROR writing to socket");
    
    printf("Réponse envoyée : %s", response);

    // 4. LE NETTOYAGE
    if (msg != NULL) free(msg);
    close(newsockfd);
}

int main(int argc, char *argv[]){
    int newsockfd, portno;
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


    // Intercepter le Ctrl+C 
    signal(SIGINT, handle_sigint);

    // Forcer la réutilisation du port 
    int opt = 1;
    if (setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) < 0) {
        error("ERROR setsockopt");
    }
    
    bzero((char *) &serv_addr, sizeof(serv_addr));

    portno = atoi(argv[1]);

    serv_addr.sin_family = AF_INET;
    //serv_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    inet_pton(AF_INET, "0.0.0.0", &serv_addr.sin_addr.s_addr);// On a remplacer 127.0.0.1 par 0.0.0.0 pour que le serveur puisse accepter les connexions de n'importe quelle adresse IP
    serv_addr.sin_port = htons(portno);

    if (bind(sockfd, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0) error("ERROR on bind()");

    // printf("=> listen() ? ...");
    // getchar(); 
    // printf("OK\n");

    printf("=> Serveur en écoute sur le port %d...\n", portno);
    listen(sockfd, 5);

    // printf("=> accept() ? ...");
    // getchar(); 
    // printf("OK\n");

    cli_socket_len = sizeof(cli_addr);
    newsockfd = accept(sockfd, (struct sockaddr *) &cli_addr, &cli_socket_len);
    if (newsockfd < 0) error("ERROR on accept()");

    bzero(buffer, BUFLEN);

    // printf("=> read() ? ...");
    // getchar(); 
    // printf("OK\n");

    n = read(newsockfd, buffer, BUFLEN - 1); //on a modifié la taille de buffer lu(10)
    if (n < 0) error("ERROR reading from socket");
    printf("Here is the message: (size: %d bytes) %s\n", n, buffer);

    // printf("=> write() ? ...");
    // getchar(); 
    // printf("OK\n");

    // n = write(newsockfd, "I got your message",18);
    // if (n < 0) error("ERROR writing to socket");

    MessageP2P *msg = add_message(buffer);
  
    anwser(msg, newsockfd, n);
    close(sockfd);

    return 0; 
}
