package com.marketplace.backend.config;

import com.marketplace.backend.category.Category;
import com.marketplace.backend.category.CategoryRepository;
import com.marketplace.backend.delivery.DeliveryZone;
import com.marketplace.backend.delivery.DeliveryZoneRepository;
import com.marketplace.backend.delivery.PickupLocation;
import com.marketplace.backend.delivery.PickupLocationRepository;
import com.marketplace.backend.finance.PayoutSchedule;
import com.marketplace.backend.finance.VendorFinanceConfig;
import com.marketplace.backend.finance.VendorFinanceConfigRepository;
import com.marketplace.backend.product.Product;
import com.marketplace.backend.product.ProductRepository;
import com.marketplace.backend.product.ProductStatus;
import com.marketplace.backend.user.Role;
import com.marketplace.backend.user.RoleName;
import com.marketplace.backend.user.RoleRepository;
import com.marketplace.backend.user.User;
import com.marketplace.backend.user.UserRepository;
import com.marketplace.backend.user.UserRole;
import com.marketplace.backend.user.UserRoleRepository;
import com.marketplace.backend.user.UserStatus;
import com.marketplace.backend.vendor.Vendor;
import com.marketplace.backend.vendor.VendorRepository;
import com.marketplace.backend.vendor.VendorStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initializeData(
            RoleRepository roleRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            VendorRepository vendorRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            VendorFinanceConfigRepository financeConfigRepository,
            DeliveryZoneRepository deliveryZoneRepository,
            PickupLocationRepository pickupLocationRepository,
            PasswordEncoder passwordEncoder,
            @Value("${demo.seed.enabled:false}") boolean demoSeedEnabled,
            @Value("${demo.seed.admin-password:}") String adminPassword,
            @Value("${demo.seed.user-password:}") String userPassword
    ) {
        return args -> {
            ensureRoles(roleRepository);

            if (!demoSeedEnabled) {
                return;
            }

            if (adminPassword.isBlank() || userPassword.isBlank()) {
                throw new IllegalStateException(
                        "Demo seed is enabled but demo passwords are not configured"
                );
            }

            Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                    .orElseThrow();
            Role vendorRole = roleRepository.findByName(RoleName.VENDOR)
                    .orElseThrow();
            Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                    .orElseThrow();

            User admin = ensureUser(
                    userRepository,
                    passwordEncoder,
                    "admin.demo@example.com",
                    "Marketplace Admin",
                    adminPassword
            );

            ensureUserRole(userRoleRepository, admin, customerRole);
            ensureUserRole(userRoleRepository, admin, adminRole);

            User demoUser = ensureUser(
                    userRepository,
                    passwordEncoder,
                    "demo-customer-ada@example.com",
                    "Ada Demo",
                    userPassword
            );

            ensureUserRole(userRoleRepository, demoUser, customerRole);
            ensureUserRole(userRoleRepository, demoUser, vendorRole);

            Vendor vendor = vendorRepository.findByUserId(demoUser.getId())
                    .orElseGet(() -> {
                        Vendor created = new Vendor();
                        created.setUser(demoUser);
                        created.setBusinessName("Ada Market");
                        created.setBusinessDescription(
                                "A demo multi-category seller for the live marketplace presentation."
                        );
                        created.setStatus(VendorStatus.APPROVED);
                        created.setPaystackSubaccountCode("DEMO_SUB_001");
                        return vendorRepository.save(created);
                    });

            if (vendor.getStatus() != VendorStatus.APPROVED) {
                vendor.setStatus(VendorStatus.APPROVED);
                vendor = vendorRepository.save(vendor);
            }

            Vendor finalVendor = vendor;

            if (financeConfigRepository.findByVendorId(finalVendor.getId()).isEmpty()) {
                VendorFinanceConfig config = new VendorFinanceConfig();
                config.setVendor(finalVendor);
                config.setCommissionPercent(10);
                config.setPayoutSchedule(PayoutSchedule.WEEKLY);
                financeConfigRepository.save(config);
            }

            Category electronics = ensureCategory(
                    categoryRepository,
                    "Electronics",
                    "Phones, audio, accessories and everyday technology."
            );
            Category fashion = ensureCategory(
                    categoryRepository,
                    "Fashion",
                    "Clothing, footwear and everyday style."
            );
            Category home = ensureCategory(
                    categoryRepository,
                    "Home & Living",
                    "Furniture, lighting and home essentials."
            );
            Category beauty = ensureCategory(
                    categoryRepository,
                    "Beauty",
                    "Skincare, grooming and personal care."
            );
            Category groceries = ensureCategory(
                    categoryRepository,
                    "Groceries",
                    "Food staples and household groceries."
            );
            Category sports = ensureCategory(
                    categoryRepository,
                    "Sports",
                    "Fitness, training and active lifestyle essentials."
            );

            ensureProduct(
                    productRepository,
                    finalVendor,
                    electronics,
                    "Wireless Headphones",
                    "Comfortable over-ear wireless headphones for everyday listening.",
                    45000,
                    25
            );
            ensureProduct(
                    productRepository,
                    finalVendor,
                    fashion,
                    "Classic Sneakers",
                    "Versatile everyday sneakers with a clean, minimal finish.",
                    38000,
                    30
            );
            ensureProduct(
                    productRepository,
                    finalVendor,
                    home,
                    "Modern Table Lamp",
                    "Compact warm-light table lamp for bedrooms and workspaces.",
                    28000,
                    18
            );
            ensureProduct(
                    productRepository,
                    finalVendor,
                    beauty,
                    "Skincare Essentials Set",
                    "A simple daily cleanser, moisturizer and care set.",
                    22000,
                    35
            );
            ensureProduct(
                    productRepository,
                    finalVendor,
                    groceries,
                    "Premium Rice 5kg",
                    "Quality long-grain rice packed for everyday family meals.",
                    18500,
                    40
            );
            ensureProduct(
                    productRepository,
                    finalVendor,
                    sports,
                    "Training Backpack",
                    "Lightweight gym and training backpack with multiple compartments.",
                    32000,
                    20
            );

            ensureDeliveryZone(deliveryZoneRepository);
            ensurePickupLocation(pickupLocationRepository);
        };
    }

    private void ensureRoles(RoleRepository roleRepository) {
        for (RoleName roleName : RoleName.values()) {
            if (roleRepository.findByName(roleName).isEmpty()) {
                Role role = new Role();
                role.setName(roleName);
                roleRepository.save(role);
            }
        }
    }

    private User ensureUser(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            String email,
            String name,
            String password
    ) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail(email);
                    user.setName(name);
                    user.setPasswordHash(passwordEncoder.encode(password));
                    user.setStatus(UserStatus.ACTIVE);
                    user.setEmailVerified(true);
                    return userRepository.save(user);
                });
    }

    private void ensureUserRole(
            UserRoleRepository userRoleRepository,
            User user,
            Role role
    ) {
        boolean exists = userRoleRepository.findByUserId(user.getId())
                .stream()
                .anyMatch(userRole ->
                        userRole.getRole().getId().equals(role.getId())
                );

        if (!exists) {
            userRoleRepository.save(new UserRole(user, role));
        }
    }

    private Category ensureCategory(
            CategoryRepository categoryRepository,
            String name,
            String description
    ) {
        return categoryRepository.findAll()
                .stream()
                .filter(category -> category.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setName(name);
                    category.setDescription(description);
                    category.setActive(true);
                    return categoryRepository.save(category);
                });
    }

    private void ensureProduct(
            ProductRepository productRepository,
            Vendor vendor,
            Category category,
            String name,
            String description,
            long price,
            int stock
    ) {
        boolean exists = productRepository.findByVendorIdOrderByCreatedAtDesc(
                        vendor.getId()
                )
                .stream()
                .anyMatch(product -> product.getName().equalsIgnoreCase(name));

        if (exists) {
            return;
        }

        Product product = new Product();
        product.setVendor(vendor);
        product.setCategory(category);
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setStockQuantity(stock);
        product.setStatus(ProductStatus.APPROVED);

        productRepository.save(product);
    }

    private void ensureDeliveryZone(
            DeliveryZoneRepository deliveryZoneRepository
    ) {
        List<DeliveryZone> existing =
                deliveryZoneRepository.findByActiveTrueOrderByNameAsc();

        if (existing.stream().anyMatch(zone ->
                zone.getName().equalsIgnoreCase("Lagos Standard"))) {
            return;
        }

        DeliveryZone zone = new DeliveryZone();
        zone.setName("Lagos Standard");
        zone.setState("Lagos");
        zone.setShippingFee(2500);
        zone.setActive(true);
        deliveryZoneRepository.save(zone);
    }

    private void ensurePickupLocation(
            PickupLocationRepository pickupLocationRepository
    ) {
        List<PickupLocation> existing =
                pickupLocationRepository.findByActiveTrueOrderByNameAsc();

        if (existing.stream().anyMatch(location ->
                location.getName().equalsIgnoreCase("Lagos Pickup Hub"))) {
            return;
        }

        PickupLocation location = new PickupLocation();
        location.setName("Lagos Pickup Hub");
        location.setAddress("12 Demo Market Road");
        location.setCity("Lagos");
        location.setState("Lagos");
        location.setCountry("Nigeria");
        location.setActive(true);
        pickupLocationRepository.save(location);
    }
}
