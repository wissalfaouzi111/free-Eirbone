#include <assert.h>
#include <stdio.h>
#include <string.h>

#include "file.h"

static void test_insert_and_lookup(void) {
    printf("Test file 1: insertion + recherche... ");

    FichierInfo *f = creer_fichier_info("alpha.bin", 4096, 1024,
                                        "0123456789abcdef0123456789abcdef");

    assert(f != NULL);

    FichierInfo *found = trouver_fichier_par_cle("0123456789abcdef0123456789abcdef");
    assert(found != NULL);
    assert(strcmp(found->nom, "alpha.bin") == 0);
    assert(found->taille == 4096);
    assert(found->taille_piece == 1024);

    assert(strcmp(get_name("0123456789abcdef0123456789abcdef"), "alpha.bin") == 0);
    assert(get_size("0123456789abcdef0123456789abcdef") == 4096);
    assert(get_piece_size("0123456789abcdef0123456789abcdef") == 1024);

    liberer_fichier_info(f);
    printf("OK!\n");
}

static void test_update_existing_key(void) {
    printf("Test file 2: update sur cle existante... ");

    FichierInfo *f1 = creer_fichier_info("before.txt", 100, 1024,
                                         "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    assert(f1 != NULL);

    FichierInfo *f2 = creer_fichier_info("after.txt", 200, 2048,
                                         "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    assert(f2 != NULL);

    assert(f1 == f2);
    assert(strcmp(f2->nom, "after.txt") == 0);
    assert(f2->taille == 200);
    assert(f2->taille_piece == 2048);

    liberer_fichier_info(f2);
    printf("OK!\n");
}

static void test_not_found_and_null_safety(void) {
    printf("Test file 3: cle absente + null safety... ");

    assert(trouver_fichier_par_cle("ffffffffffffffffffffffffffffffff") == NULL);
    assert(trouver_fichier_par_cle(NULL) == NULL);

    assert(get_name("ffffffffffffffffffffffffffffffff") == NULL);
    assert(get_size("ffffffffffffffffffffffffffffffff") == -1);
    assert(get_piece_size("ffffffffffffffffffffffffffffffff") == -1);

    liberer_fichier_info(NULL);
    printf("OK!\n");
}

int main(void) {
    test_insert_and_lookup();
    test_update_existing_key();
    test_not_found_and_null_safety();

    printf("\nTous les tests file.c sont valides.\n");
    return 0;
}
