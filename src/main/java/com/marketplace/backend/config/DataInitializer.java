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
            ensureProduct(productRepository, finalVendor, electronics, "Smart Watch", "Everyday smart watch with activity tracking and notifications.", 52000, 22);
            ensureProduct(productRepository, finalVendor, electronics, "Portable Bluetooth Speaker", "Compact wireless speaker with clear sound and long battery life.", 27000, 28);
            ensureProduct(productRepository, finalVendor, electronics, "Fast Charge Power Bank", "High-capacity portable charger for phones and accessories.", 24000, 34);
            ensureProduct(productRepository, finalVendor, electronics, "USB-C Charging Hub", "Multi-port charging hub for phones, tablets and accessories.", 19500, 26);

            ensureProduct(productRepository, finalVendor, fashion, "Everyday Tote Bag", "Roomy everyday tote with a clean structured shape.", 26000, 24);
            ensureProduct(productRepository, finalVendor, fashion, "Casual Polo Shirt", "Soft everyday polo shirt with a relaxed fit.", 18000, 36);
            ensureProduct(productRepository, finalVendor, fashion, "Leather Crossbody Bag", "Compact crossbody bag for daily essentials.", 34000, 19);
            ensureProduct(productRepository, finalVendor, fashion, "Minimal Wrist Watch", "Simple analogue wrist watch with a versatile everyday design.", 41000, 17);

            ensureProduct(productRepository, finalVendor, home, "Cotton Bedsheet Set", "Soft bedsheet set designed for everyday comfort.", 24500, 21);
            ensureProduct(productRepository, finalVendor, home, "Storage Basket Set", "Woven storage baskets for bedrooms, shelves and living spaces.", 16500, 30);
            ensureProduct(productRepository, finalVendor, home, "Ceramic Dinner Set", "Modern ceramic dinnerware set for everyday meals.", 36000, 16);
            ensureProduct(productRepository, finalVendor, home, "Electric Kettle", "Quick-boil electric kettle for tea, coffee and kitchen use.", 23000, 27);

            ensureProduct(productRepository, finalVendor, beauty, "Vitamin C Face Serum", "Lightweight brightening serum for a simple daily routine.", 14500, 42);
            ensureProduct(productRepository, finalVendor, beauty, "Hydrating Body Lotion", "Daily moisturizing lotion with a light, non-greasy finish.", 12000, 38);
            ensureProduct(productRepository, finalVendor, beauty, "Grooming Kit", "Compact personal grooming kit for home and travel.", 20500, 25);
            ensureProduct(productRepository, finalVendor, beauty, "Daily Sunscreen SPF 50", "Lightweight daily sunscreen for broad-spectrum protection.", 13500, 32);

            ensureProduct(productRepository, finalVendor, groceries, "Vegetable Cooking Oil 5L", "Family-size vegetable cooking oil for everyday meals.", 21500, 31);
            ensureProduct(productRepository, finalVendor, groceries, "Breakfast Cereal Pack", "Crunchy breakfast cereal pack for quick morning meals.", 9500, 45);
            ensureProduct(productRepository, finalVendor, groceries, "Tomato Paste Carton", "Multi-pack tomato paste carton for regular home cooking.", 15500, 29);
            ensureProduct(productRepository, finalVendor, groceries, "Instant Noodles Family Pack", "Family-size pack of instant noodles for quick meals.", 12500, 50);

            ensureProduct(productRepository, finalVendor, sports, "Yoga Mat", "Non-slip exercise mat for stretching, yoga and floor workouts.", 17000, 33);
            ensureProduct(productRepository, finalVendor, sports, "Resistance Band Set", "Multi-resistance band set for home and gym training.", 14500, 37);
            ensureProduct(productRepository, finalVendor, sports, "Insulated Sports Bottle", "Reusable insulated bottle for workouts and daily hydration.", 11000, 41);
            ensureProduct(productRepository, finalVendor, sports, "Adjustable Dumbbell Pair", "Compact adjustable dumbbell pair for strength training.", 68000, 14);

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
        long priceInKobo = Math.multiplyExact(price, 100L);

        Product existing = productRepository.findByVendorIdOrderByCreatedAtDesc(
                        vendor.getId()
                )
                .stream()
                .filter(product -> product.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            if (existing.getPrice() != priceInKobo) {
                existing.setPrice(priceInKobo);
                productRepository.save(existing);
            }
            return;
        }

        Product product = new Product();
        product.setVendor(vendor);
        product.setCategory(category);
        product.setName(name);
        product.setDescription(description);
        product.setPrice(priceInKobo);
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
