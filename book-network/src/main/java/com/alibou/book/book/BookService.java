package com.alibou.book.book;

import com.alibou.book.common.PageResponse;
import com.alibou.book.exception.OperationNotPermittedException;
import com.alibou.book.file.FileStorageService;
import com.alibou.book.history.BookTransactionHistory;
import com.alibou.book.history.BookTransactionHistoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;

import static com.alibou.book.book.BookSpecification.withOwnerId;

// Annotation pour indiquer que cette classe est un service Spring
@Service
// Annotation Lombok pour générer un constructeur avec les dépendances requises
@RequiredArgsConstructor
// Annotation pour activer le logging
@Slf4j
// Annotation pour indiquer que les méthodes de cette classe doivent être exécutées dans une transaction
@Transactional
public class BookService {

    // Dépendances injectées par le constructeur
    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final BookTransactionHistoryRepository transactionHistoryRepository;
    private final FileStorageService fileStorageService;

    // Méthode pour enregistrer un livre
    public Integer save(BookRequest request, Authentication connectedUser) {
        // Mapper la requête en objet Book
        Book book = bookMapper.toBook(request);
        // Enregistrer le livre et retourner son ID
        return bookRepository.save(book).getId();
    }

    // Méthode pour trouver un livre par son ID
    public BookResponse findById(Integer bookId) {
        // Trouver le livre par son ID et le mapper en réponse, ou lancer une exception si non trouvé
        return bookRepository.findById(bookId)
                .map(bookMapper::toBookResponse)
                .orElseThrow(() -> new EntityNotFoundException("No book found with ID:: " + bookId));
    }

    // Méthode pour trouver tous les livres
    public PageResponse<BookResponse> findAllBooks(int page, int size, Authentication connectedUser) {
        // Définir la pagination et le tri
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        // Trouver tous les livres affichables
        Page<Book> books = bookRepository.findAllDisplayableBooks(pageable, connectedUser.getName());
        // Mapper les livres en réponses
        List<BookResponse> booksResponse = books.stream()
                .map(bookMapper::toBookResponse)
                .toList();
        // Retourner la réponse paginée
        return new PageResponse<>(
                booksResponse,
                books.getNumber(),
                books.getSize(),
                books.getTotalElements(),
                books.getTotalPages(),
                books.isFirst(),
                books.isLast()
        );
    }

    // Méthode pour trouver tous les livres d'un propriétaire
    public PageResponse<BookResponse> findAllBooksByOwner(int page, int size, Authentication connectedUser) {
        // Définir la pagination et le tri
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        // Trouver tous les livres par propriétaire
        Page<Book> books = bookRepository.findAll(withOwnerId(connectedUser.getName()), pageable);
        // Mapper les livres en réponses
        List<BookResponse> booksResponse = books.stream()
                .map(bookMapper::toBookResponse)
                .toList();
        // Retourner la réponse paginée
        return new PageResponse<>(
                booksResponse,
                books.getNumber(),
                books.getSize(),
                books.getTotalElements(),
                books.getTotalPages(),
                books.isFirst(),
                books.isLast()
        );
    }

    // Méthode pour mettre à jour le statut partageable d'un livre
    public Integer updateShareableStatus(Integer bookId, Authentication connectedUser) {
        // Trouver le livre par son ID ou lancer une exception
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("No book found with ID:: " + bookId));
        // Vérifier si l'utilisateur connecté est le propriétaire du livre
        if (!Objects.equals(book.getCreatedBy(), connectedUser.getName())) {
            throw new OperationNotPermittedException("You cannot update others books shareable status");
        }
        // Mettre à jour le statut partageable et enregistrer
        book.setShareable(!book.isShareable());
        bookRepository.save(book);
        return bookId;
    }

    // Méthode pour mettre à jour le statut archivé d'un livre
    public Integer updateArchivedStatus(Integer bookId, Authentication connectedUser) {
        // Trouver le livre par son ID ou lancer une exception
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("No book found with ID:: " + bookId));
        // Vérifier si l'utilisateur connecté est le propriétaire du livre
        if (!Objects.equals(book.getCreatedBy(), connectedUser.getName())) {
            throw new OperationNotPermittedException("You cannot update others books archived status");
        }
        // Mettre à jour le statut archivé et enregistrer
        book.setArchived(!book.isArchived());
        bookRepository.save(book);
        return bookId;
    }

    // Méthode pour emprunter un livre
    public Integer borrowBook(Integer bookId, Authentication connectedUser) {
        // Trouver le livre par son ID ou lancer une exception
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("No book found with ID:: " + bookId));
        // Vérifier si le livre est archivé ou non partageable
        if (book.isArchived() || !book.isShareable()) {
            throw new OperationNotPermittedException("The requested book cannot be borrowed since it is archived or not shareable");
        }
        // Vérifier si l'utilisateur connecté est le propriétaire du livre
        if (Objects.equals(book.getCreatedBy(), connectedUser.getName())) {
            throw new OperationNotPermittedException("You cannot borrow your own book");
        }
        // Vérifier si l'utilisateur a déjà emprunté ce livre
        final boolean isAlreadyBorrowedByUser = transactionHistoryRepository.isAlreadyBorrowedByUser(bookId, connectedUser.getName());
        if (isAlreadyBorrowedByUser) {
            throw new OperationNotPermittedException("You already borrowed this book and it is still not returned or the return is not approved by the owner");
        }
        // Vérifier si le livre est déjà emprunté par un autre utilisateur
        final boolean isAlreadyBorrowedByOtherUser = transactionHistoryRepository.isAlreadyBorrowed(bookId);
        if (isAlreadyBorrowedByOtherUser) {
            throw new OperationNotPermittedException("The requested book is already borrowed");
        }
        // Enregistrer l'emprunt du livre
        BookTransactionHistory bookTransactionHistory = BookTransactionHistory.builder()
                .userId(connectedUser.getName())
                .book(book)
                .returned(false)
                .returnApproved(false)
                .build();
        return transactionHistoryRepository.save(bookTransactionHistory).getId();
    }

    // Méthode pour retourner un livre emprunté
    public Integer returnBorrowedBook(Integer bookId, Authentication connectedUser) {
        // Trouver le livre par son ID ou lancer une exception
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("No book found with ID:: " + bookId));
        // Vérifier si le livre est archivé ou non partageable
        if (book.isArchived() || !book.isShareable()) {
            throw new OperationNotPermittedException("The requested book is archived or not shareable");
        }
        // Vérifier si l'utilisateur connecté est le propriétaire du livre
        if (Objects.equals(book.getCreatedBy(), connectedUser.getName())) {
            throw new OperationNotPermittedException("You cannot borrow or return your own book");
        }
        // Trouver l'historique de transaction du livre par ID de livre et d'utilisateur
        BookTransactionHistory bookTransactionHistory = transactionHistoryRepository.findByBookIdAndUserId(bookId, connectedUser.getName())
                .orElseThrow(() -> new OperationNotPermittedException("You did not borrow this book"));
        // Mettre à jour le statut de retour et enregistrer
        bookTransactionHistory.setReturned(true);
        return transactionHistoryRepository.save(bookTransactionHistory).getId();
    }

    // Méthode pour approuver le retour d'un livre emprunté
    public Integer approveReturnBorrowedBook(Integer bookId, Authentication connectedUser) {
        // Trouver le livre par son ID ou lancer une exception
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("No book found with ID:: " + bookId));
        // Vérifier si le livre est archivé ou non partageable
        if (book.isArchived() || !book.isShareable()) {
            throw new OperationNotPermittedException("The requested book is archived or not shareable");
        }
        // Vérifier si l'utilisateur connecté est le propriétaire du livre
        if (!Objects.equals(book.getCreatedBy(), connectedUser.getName())) {
            throw new OperationNotPermittedException("You cannot approve the return of a book you do not own");
        }
        // Trouver l'historique de transaction du livre par ID de livre et de propriétaire
        BookTransactionHistory bookTransactionHistory = transactionHistoryRepository.findByBookIdAndOwnerId(bookId, connectedUser.getName())
                .orElseThrow(() -> new OperationNotPermittedException("The book is not returned yet. You cannot approve its return"));
        // Mettre à jour le statut d'approbation du retour et enregistrer
        bookTransactionHistory.setReturnApproved(true);
        return transactionHistoryRepository.save(bookTransactionHistory).getId();
    }

    // Méthode pour télécharger une photo de couverture de livre
    public void uploadBookCoverPicture(MultipartFile file, Authentication connectedUser, Integer bookId) {
        // Trouver le livre par son ID ou lancer une exception
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("No book found with ID:: " + bookId));
        // Enregistrer le fichier de couverture et mettre à jour le livre
        var profilePicture = fileStorageService.saveFile(file, connectedUser.getName());
        book.setBookCover(profilePicture);
        bookRepository.save(book);
    }

    // Méthode pour trouver tous les livres empruntés
    public PageResponse<BorrowedBookResponse> findAllBorrowedBooks(int page, int size, Authentication connectedUser) {
        // Définir la pagination et le tri
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        // Trouver tous les livres empruntés
        Page<BookTransactionHistory> allBorrowedBooks = transactionHistoryRepository.findAllBorrowedBooks(pageable, connectedUser.getName());
        // Mapper les livres en réponses
        List<BorrowedBookResponse> booksResponse = allBorrowedBooks.stream()
                .map(bookMapper::toBorrowedBookResponse)
                .toList();
        // Retourner la réponse paginée
        return new PageResponse<>(
                booksResponse,
                allBorrowedBooks.getNumber(),
                allBorrowedBooks.getSize(),
                allBorrowedBooks.getTotalElements(),
                allBorrowedBooks.getTotalPages(),
                allBorrowedBooks.isFirst(),
                allBorrowedBooks.isLast()
        );
    }

    // Méthode pour trouver tous les livres retournés
    public PageResponse<BorrowedBookResponse> findAllReturnedBooks(int page, int size, Authentication connectedUser) {
        // Définir la pagination et le tri
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        // Trouver tous les livres retournés
        Page<BookTransactionHistory> allBorrowedBooks = transactionHistoryRepository.findAllReturnedBooks(pageable, connectedUser.getName());
        // Mapper les livres en réponses
        List<BorrowedBookResponse> booksResponse = allBorrowedBooks.stream()
                .map(bookMapper::toBorrowedBookResponse)
                .toList();
        // Retourner la réponse paginée
        return new PageResponse<>(
                booksResponse,
                allBorrowedBooks.getNumber(),
                allBorrowedBooks.getSize(),
                allBorrowedBooks.getTotalElements(),
                allBorrowedBooks.getTotalPages(),
                allBorrowedBooks.isFirst(),
                allBorrowedBooks.isLast()
        );
    }
}
