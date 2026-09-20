package com.companytracker.service;

import com.companytracker.domain.Company;
import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.NoteRepository;
import com.companytracker.repo.ResourceItemRepository;
import com.companytracker.security.CurrentUserService;
import com.companytracker.security.IdempotencyService;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.storage.FileStorageService;
import com.companytracker.web.error.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoteResourceServiceTest {

    @Mock NoteRepository noteRepository;
    @Mock ResourceItemRepository resourceItemRepository;
    @Mock CompanyService companyService;
    @Mock JobApplicationService jobApplicationService;
    @Mock RoundService roundService;
    @Mock OwnershipGuard ownershipGuard;
    @Mock CurrentUserService currentUserService;
    @Mock IdempotencyService idempotencyService;
    @Mock FileStorageService fileStorageService;

    NoteResourceService service;

    @BeforeEach
    void setUp() {
        service = new NoteResourceService(
                noteRepository, resourceItemRepository, companyService, jobApplicationService,
                roundService, ownershipGuard, currentUserService, idempotencyService, fileStorageService);
        when(currentUserService.requireUser()).thenReturn(new TrackerUserDetails(user(1L)));
        lenient().when(idempotencyService.execute(anyString(), anyString(), any(),
                        ArgumentMatchers.<Supplier<Object>>any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
    }

    @Test
    void addNoteRequiresTitleAndBody() {
        AppException ex = assertThrows(AppException.class,
                () -> service.addNote("company", 1L, " ", "body", "k"));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void addNoteRejectsUnknownParent() {
        AppException ex = assertThrows(AppException.class,
                () -> service.addNote("galaxy", 1L, "t", "b", "k"));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void addResourceRequiresUrlOrFile() {
        AppException ex = assertThrows(AppException.class,
                () -> service.addResource("company", 1L, "link", null, null, "k"));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    private UserAccount user(Long id) {
        UserAccount u = new UserAccount();
        u.setEmail(id + "@t.com");
        u.setPasswordHash("h");
        u.setRole(UserRole.USER);
        u.setEnabled(true);
        u.setSecurityStamp(UUID.randomUUID());
        try {
            var f = UserAccount.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return u;
    }
}
