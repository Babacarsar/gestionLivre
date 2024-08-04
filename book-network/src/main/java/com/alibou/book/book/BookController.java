package com.alibou.book.book;

import com.alibou.book.common.PageResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;

// Annotation RestController indique que cette classe est un contrôleur REST
@RestController
// Mapping de la route de base pour ce contrôleur
@RequestMapping("books")
// Annotation Lombok pour générer un constructeur avec tous les champs requis (finals)
@RequiredArgsConstructor
// Annotation Swagger pour documenter ce contrôleur
@Tag(name = "Book")
public class BookController {

    // Service pour gérer la logique métier des livres
    private final BookService service;

    // Endpoint pour sauvegarder un livre
    @PostMapping
    public ResponseEntity<Integer> saveBook(
            @Valid @RequestBody BookRequest request, // Validation de la requête
            Authentication connectedUser // L'utilisateur authentifié
    ) {
        // Appelle le service pour sauvegarder le livre et retourne l'ID du livre sauvegardé
        return ResponseEntity.ok(service.save(request, connectedUser));
    }

    // Endpoint pour récupérer un livre par son ID
    @GetMapping("/{book-id}")
    public ResponseEntity<BookResponse> findBookById(
            @PathVariable("book-id") Integer bookId // ID du livre dans l'URL
    ) {
        // Appelle le service pour trouver le livre par son ID et retourne les détails du livre
        return ResponseEntity.ok(service.findById(bookId));
    }

    // Endpoint pour récupérer tous les livres avec pagination
    @GetMapping
    public ResponseEntity<PageResponse<BookResponse>> findAllBooks(
            @RequestParam(name = "page", defaultValue = "0", required = false) int page, // Page courante (optionnelle)
            @RequestParam(name = "size", defaultValue = "10", required = false) int size, // Taille de la page (optionnelle)
            Authentication connectedUser // L'utilisateur authentifié
    ) {
        // Appelle le service pour trouver tous les livres avec pagination et retourne les résultats
        return ResponseEntity.ok(service.findAllBooks(page, size, connectedUser));
    }

    // Endpoint pour récupérer tous les livres possédés par l'utilisateur authentifié
    @GetMapping("/owner")
    public ResponseEntity<PageResponse<BookResponse>> findAllBooksByOwner(
            @RequestParam(name = "page", defaultValue = "0", required = false) int page, // Page courante (optionnelle)
            @RequestParam(name = "size", defaultValue = "10", required = false) int size, // Taille de la page (optionnelle)
            Authentication connectedUser // L'utilisateur authentifié
    ) {
        // Appelle le service pour trouver tous les livres possédés par l'utilisateur et retourne les résultats
        return ResponseEntity.ok(service.findAllBooksByOwner(page, size, connectedUser));
    }

    // Endpoint pour récupérer tous les livres empruntés par l'utilisateur authentifié
    @GetMapping("/borrowed")
    public ResponseEntity<PageResponse<BorrowedBookResponse>> findAllBorrowedBooks(
            @RequestParam(name = "page", defaultValue = "0", required = false) int page, // Page courante (optionnelle)
            @RequestParam(name = "size", defaultValue = "10", required = false) int size, // Taille de la page (optionnelle)
            Authentication connectedUser // L'utilisateur authentifié
    ) {
        // Appelle le service pour trouver tous les livres empruntés par l'utilisateur et retourne les résultats
        return ResponseEntity.ok(service.findAllBorrowedBooks(page, size, connectedUser));
    }

    // Endpoint pour récupérer tous les livres retournés par l'utilisateur authentifié
    @GetMapping("/returned")
    public ResponseEntity<PageResponse<BorrowedBookResponse>> findAllReturnedBooks(
            @RequestParam(name = "page", defaultValue = "0", required = false) int page, // Page courante (optionnelle)
            @RequestParam(name = "size", defaultValue = "10", required = false) int size, // Taille de la page (optionnelle)
            Authentication connectedUser // L'utilisateur authentifié
    ) {
        // Appelle le service pour trouver tous les livres retournés par l'utilisateur et retourne les résultats
        return ResponseEntity.ok(service.findAllReturnedBooks(page, size, connectedUser));
    }

    // Endpoint pour mettre à jour le statut de partage d'un livre
    @PatchMapping("/shareable/{book-id}")
    public ResponseEntity<Integer> updateShareableStatus(
            @PathVariable("book-id") Integer bookId, // ID du livre dans l'URL
            Authentication connectedUser // L'utilisateur authentifié
    ) {
        // Appelle le service pour mettre à jour le statut de partage du livre et retourne l'ID du livre
        return ResponseEntity.ok(service.updateShareableStatus(bookId, connectedUser));
    }

    // Endpoint pour mettre à jour le statut d'archivage d'un livre
    @PatchMapping("/archived/{book-id}")
    public ResponseEntity<Integer> updateArchivedStatus(
            @PathVariable("book-id") Integer bookId, // ID du livre dans l'URL
            Authentication connectedUser // L'utilisateur authentifié
    ) {
        // Appelle le service pour mettre à jour le statut d'archivage du livre et retourne l'ID du livre
        return ResponseEntity.ok(service.updateArchivedStatus(bookId, connectedUser));
    }

    // Endpoint pour emprunter un livre
    @PostMapping("borrow/{book-id}")
    public ResponseEntity<Integer> borrowBook(
            @PathVariable("book-id") Integer bookId, // ID du livre dans l'URL
            Authentication connectedUser // L'utilisateur authentifié
    ) {
        // Appelle le service pour emprunter le livre et retourne l'ID du livre emprunté
        return ResponseEntity.ok(service.borrowBook(bookId, connectedUser));
    }

    // Endpoint pour retourner un livre emprunté
    @PatchMapping("borrow/return/{book-id}")
    public ResponseEntity<Integer> returnBorrowBook(
            @PathVariable("book-id") Integer bookId, // ID du livre dans l'URL
            Authentication connectedUser // L'utilisateur authentifié
    ) {
        // Appelle le service pour retourner le livre emprunté et retourne l'ID du livre retourné
        return ResponseEntity.ok(service.returnBorrowedBook(bookId, connectedUser));
    }

    // Endpoint pour approuver le retour d'un livre emprunté
    @PatchMapping("borrow/return/approve/{book-id}")
    public ResponseEntity<Integer> approveReturnBorrowBook(
            @PathVariable("book-id") Integer bookId, // ID du livre dans l'URL
            Authentication connectedUser // L'utilisateur authentifié
    ) {
        // Appelle le service pour approuver le retour du livre emprunté et retourne l'ID du livre retourné
        return ResponseEntity.ok(service.approveReturnBorrowedBook(bookId, connectedUser));
    }

    // Endpoint pour uploader une image de couverture pour un livre
    @PostMapping(value = "/cover/{book-id}", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadBookCoverPicture(
            @PathVariable("book-id") Integer bookId, // ID du livre dans l'URL
            @Parameter() @RequestPart("file") MultipartFile file, // Fichier de la couverture uploadé
            Authentication connectedUser // L'utilisateur authentifié
    ) {
        // Appelle le service pour uploader la couverture du livre
        service.uploadBookCoverPicture(file, connectedUser, bookId);
        // Retourne une réponse acceptée sans contenu
        return ResponseEntity.accepted().build();
    }
}
