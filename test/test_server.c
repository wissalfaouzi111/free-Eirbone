#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <assert.h>

int main(int argc, char *argv[]) {
    if (argc < 2) exit(1);

    int sockfd = socket(AF_INET, SOCK_STREAM, 0);
    struct sockaddr_in serv_addr;
    memset(&serv_addr, 0, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr.s_addr);
    serv_addr.sin_port = htons(atoi(argv[1]));

    assert(connect(sockfd, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) == 0);

    char buffer[256];
    
    // Test 1: Message valide
    write(sockfd, "announce listen 6881 seed [ video.mp4 2048 1024 hash1234 ] leech [ ]\n", 69);
    memset(buffer, 0, 256);
    read(sockfd, buffer, 255);
    assert(strcmp(buffer, "ok\n") == 0);

    // Test 2: Message look valide (le fichier a été ajouté par le announce)
    write(sockfd, "look [ video.mp4 ]\n", 19);
    memset(buffer, 0, 256);
    read(sockfd, buffer, 255);
    assert(strstr(buffer, "list [ video.mp4 2048 1024 hash1234 ]\n") != NULL);

    // Test 3: 3 messages invalides consécutifs
    for(int i=0; i<3; i++) {
        write(sockfd, "badcommand\n", 11);
        memset(buffer, 0, 256);
        read(sockfd, buffer, 255);
        assert(strcmp(buffer, "ERROR Unknown command\n") == 0);
    }

    // Le serveur doit fermer la connexion. Un 4e write/read échouera ou retournera 0.
    write(sockfd, "look [ video.mp4 ]\n", 19);
    int n = read(sockfd, buffer, 255);
    assert(n == 0); // 0 indique que le serveur a fermé le socket (règle des 3 erreurs)

    printf("\n✅ TEST D'INTEGRATION ET KEEP-ALIVE REUSSI\n");
    close(sockfd);
    return 0;
}