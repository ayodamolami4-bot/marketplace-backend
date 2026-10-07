package com.marketplace.backend.vendor;

import com.marketplace.backend.notification.NotificationService;
import com.marketplace.backend.user.Role;
import com.marketplace.backend.user.RoleName;
import com.marketplace.backend.user.RoleRepository;
import com.marketplace.backend.user.User;
import com.marketplace.backend.user.UserRepository;
import com.marketplace.backend.user.UserRole;
import com.marketplace.backend.user.UserRoleRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class VendorService {

    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final NotificationService notificationService;

    public VendorService(
            VendorRepository vendorRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            NotificationService notificationService
    ) {
        this.vendorRepository = vendorRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public VendorResponse apply(
            UUID userId,
            VendorApplicationRequest request
    ) {
        User user = getUser(userId);

        Optional<Vendor> existingVendor =
                vendorRepository.findByUserId(userId);

        if (existingVendor.isPresent()) {
            Vendor vendor = existingVendor.get();

            if (vendor.getStatus() != VendorStatus.REJECTED) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Vendor application already exists"
                );
            }

            updateApplicationDetails(vendor, request);
            vendor.setStatus(VendorStatus.PENDING);

            Vendor savedVendor =
                    vendorRepository.save(vendor);

            notifyAdminsOfApplication(
                    user,
                    savedVendor
            );

            return toResponse(savedVendor);
        }

        Vendor vendor = new Vendor();
        vendor.setUser(user);

        updateApplicationDetails(vendor, request);

        vendor.setStatus(VendorStatus.PENDING);

        Vendor savedVendor =
                vendorRepository.save(vendor);

        notifyAdminsOfApplication(
                user,
                savedVendor
        );

        return toResponse(savedVendor);
    }

    @Transactional
    public List<VendorResponse> getPendingApplications() {
        return vendorRepository
                .findByStatusOrderByCreatedAtAsc(
                        VendorStatus.PENDING
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public VendorResponse approve(
            UUID vendorId,
            String paystackSubaccountCode
    ) {
        Vendor vendor = getVendor(vendorId);

        if (vendor.getStatus() != VendorStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending applications can be approved"
            );
        }

        if (paystackSubaccountCode == null ||
                paystackSubaccountCode.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Paystack subaccount code is required"
            );
        }

        Role vendorRole =
                roleRepository.findByName(RoleName.VENDOR)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "VENDOR role is not configured"
                                )
                        );

        User user = vendor.getUser();

        boolean alreadyHasVendorRole =
                userRoleRepository
                        .findByUserId(user.getId())
                        .stream()
                        .anyMatch(userRole ->
                                userRole
                                        .getRole()
                                        .getName()
                                        == RoleName.VENDOR
                        );

        if (!alreadyHasVendorRole) {
            userRoleRepository.save(
                    new UserRole(
                            user,
                            vendorRole
                    )
            );
        }

        vendor.setPaystackSubaccountCode(
                paystackSubaccountCode.trim()
        );

        vendor.setStatus(
                VendorStatus.APPROVED
        );

        Vendor savedVendor =
                vendorRepository.save(vendor);

        notificationService.createNotification(
                user,
                "Seller application approved",
                "Your seller application has been approved. Seller Center is now available."
        );

        return toResponse(savedVendor);
    }

    @Transactional
    public VendorResponse reject(UUID vendorId) {
        Vendor vendor = getVendor(vendorId);

        if (vendor.getStatus() != VendorStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending applications can be rejected"
            );
        }

        vendor.setStatus(
                VendorStatus.REJECTED
        );

        Vendor savedVendor =
                vendorRepository.save(vendor);

        notificationService.createNotification(
                vendor.getUser(),
                "Seller application not approved",
                "Your seller application was not approved. You can update your details and apply again."
        );

        return toResponse(savedVendor);
    }

    private void notifyAdminsOfApplication(
            User applicant,
            Vendor vendor
    ) {
        List<UserRole> adminRoles =
                userRoleRepository
                        .findByRole_Name(
                                RoleName.ADMIN
                        );

        for (UserRole adminRole : adminRoles) {
            notificationService.createNotification(
                    adminRole.getUser(),
                    "New seller application",
                    applicant.getName()
                            + " submitted a seller application for "
                            + vendor.getBusinessName()
                            + "."
            );
        }
    }

    private void updateApplicationDetails(
            Vendor vendor,
            VendorApplicationRequest request
    ) {
        vendor.setBusinessName(
                request.getBusinessName()
                        .trim()
        );

        if (request.getBusinessDescription() == null) {
            vendor.setBusinessDescription(null);
        } else {
            vendor.setBusinessDescription(
                    request
                            .getBusinessDescription()
                            .trim()
            );
        }
    }

    private User getUser(UUID userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found"
                        )
                );
    }

    private Vendor getVendor(UUID vendorId) {
        return vendorRepository
                .findById(vendorId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Vendor application not found"
                        )
                );
    }

    private VendorResponse toResponse(
            Vendor vendor
    ) {
        User user = vendor.getUser();

        return new VendorResponse(
                vendor.getId(),
                user.getId(),
                user.getName(),
                user.getEmail(),
                vendor.getBusinessName(),
                vendor.getBusinessDescription(),
                vendor.getStatus(),
                vendor.getCreatedAt(),
                vendor.getUpdatedAt()
        );
    }
}