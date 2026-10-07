package com.marketplace.backend;

import com.marketplace.backend.user.*;
import com.marketplace.backend.coupon.*;
import com.marketplace.backend.cart.CartItemRepository;
import com.marketplace.backend.order.*;
import com.marketplace.backend.vendor.*;
import com.marketplace.backend.checkout.*;
import tools.jackson.databind.json.JsonMapper;
import com.marketplace.backend.auth.RegisterRequest;
import com.marketplace.backend.auth.ActiveAccountValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidationAndCouponTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    @Test void suspendedAccountsCannotUsePreviouslyIssuedTokens() {
        UUID id = UUID.randomUUID(); var users = mock(UserRepository.class); User user = new User();
        when(users.findById(id)).thenReturn(Optional.of(user));
        var validator = new ActiveAccountValidator(users);
        Jwt token = Jwt.withTokenValue("synthetic-test-token").header("alg", "HS256").subject(id.toString()).build();
        assertFalse(validator.validate(token).hasErrors());
        user.setStatus(UserStatus.SUSPENDED); assertTrue(validator.validate(token).hasErrors());
        user.setStatus(UserStatus.ACTIVE); assertFalse(validator.validate(token).hasErrors());
        when(users.findById(id)).thenReturn(Optional.empty()); assertTrue(validator.validate(token).hasErrors());
    }
    @Test void signupRejectsNumericNamesAndPasswordsBeyondBcryptByteLimit() {
        RegisterRequest request = new RegisterRequest(); request.setEmail("test@example.com"); request.setName("1234"); request.setPassword("á".repeat(40));
        assertFalse(validator.validate(request).isEmpty());
        request.setName("Ayo O'Neil"); request.setPassword("a".repeat(72)); assertTrue(validator.validate(request).isEmpty());
        request.setPassword("a".repeat(73)); assertFalse(validator.validate(request).isEmpty());
    }
    private AddressRequest address(String city, String state, String country, String phone, String line) {
        return new AddressRequest("Ayo O'Neil", phone, line, city, state, country, "", false);
    }
    @Test void validInternationalAddressesAreAccepted() {
        assertTrue(validator.validate(address("São Paulo", "Île-de-France", "Nigeria", "+234 901 234 5678", "12 Allen Avenue")).isEmpty());
    }
    @Test void numericCitiesStatesAndUnknownCountriesAreRejected() {
        for (var request : List.of(address("12345", "Lagos", "Nigeria", "09012345678", "12 Allen Avenue"), address("Lagos", "...", "Nigeria", "09012345678", "12 Allen Avenue"), address("Lagos", "Lagos", "Invented country", "09012345678", "12 Allen Avenue"))) {
            assertFalse(validator.validate(request).isEmpty());
        }
    }
    @Test void phoneAndAddressRejectMeaninglessValues() {
        assertFalse(validator.validate(address("Lagos", "Lagos", "Nigeria", "-------", "12 Allen Avenue")).isEmpty());
        assertFalse(validator.validate(address("Lagos", "Lagos", "Nigeria", "09012345678", "12345")).isEmpty());
    }
    @Test void couponRejectsInvalidPercentageCodeAndPastExpiry() {
        CouponRequest request = new CouponRequest(); request.setCode("!!"); request.setDiscountPercent(101); request.setExpiresAt(Instant.now().minusSeconds(60));
        assertEquals(3, validator.validate(request).size());
        request.setCode("AYO10"); request.setDiscountPercent(10); request.setExpiresAt(Instant.now().plusSeconds(3600));
        assertTrue(validator.validate(request).isEmpty());
    }
    @Test void discountOnlyAppliesToOwningSellerAndRoundsDownToKobo() {
        var repository = mock(CouponRepository.class);
        var service = new CouponDiscountService(repository, mock(CartItemRepository.class));
        UUID seller = UUID.randomUUID(), other = UUID.randomUUID();
        Coupon coupon = new Coupon(); coupon.setDiscountPercent(10); coupon.setExpiresAt(Instant.now().plusSeconds(3600));
        when(repository.findByVendorIdAndCode(seller, "AYO10")).thenReturn(Optional.of(coupon));
        when(repository.findByVendorIdAndCode(other, "AYO10")).thenReturn(Optional.empty());
        assertEquals(Map.of(seller, 10000L), service.discounts("ayo10", Map.of(seller, 100009L, other, 200000L)));
    }
    @Test void expiredAndForeignSellerCouponsCannotDiscountPurchase() {
        var repository = mock(CouponRepository.class); var service = new CouponDiscountService(repository, mock(CartItemRepository.class)); UUID seller = UUID.randomUUID();
        Coupon coupon = new Coupon(); coupon.setDiscountPercent(10); coupon.setExpiresAt(Instant.now().minusSeconds(1));
        when(repository.findByVendorIdAndCode(seller, "OLD")).thenReturn(Optional.of(coupon));
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.discounts("OLD", Map.of(seller, 100000L))).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.discounts("FOREIGN", Map.of(seller, 100000L))).getStatusCode().value());
    }
    @Test void customerCannotUpdateAnotherCustomersAddress() {
        var repository = mock(AddressRepository.class); var service = new AddressService(repository, mock(UserRepository.class));
        UUID ownerId = UUID.randomUUID(), attackerId = UUID.randomUUID(), addressId = UUID.randomUUID();
        User owner = mock(User.class); when(owner.getId()).thenReturn(ownerId); Address address = new Address(); address.setUser(owner);
        when(repository.findById(addressId)).thenReturn(Optional.of(address));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.updateAddress(attackerId, addressId, address("Lagos", "Lagos", "Nigeria", "09012345678", "12 Allen Avenue"))).getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test void customerCannotReadAnotherCustomersOrder() {
        var repository = mock(OrderRepository.class);
        var service = new OrderService(repository, mock(SubOrderRepository.class), mock(OrderItemRepository.class), mock(UserRepository.class), mock(VendorRepository.class));
        User owner = mock(User.class); when(owner.getId()).thenReturn(UUID.randomUUID());
        Order order = new Order(); order.setUser(owner); UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(order));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.getCustomerOrder(UUID.randomUUID(), id)).getStatusCode().value());
    }

    @Test void vendorCannotUpdateAnotherVendorsOrder() {
        var subOrders = mock(SubOrderRepository.class); var vendors = mock(VendorRepository.class);
        var service = new OrderService(mock(OrderRepository.class), subOrders, mock(OrderItemRepository.class), mock(UserRepository.class), vendors);
        UUID userId = UUID.randomUUID(), id = UUID.randomUUID(); Vendor attacker = mock(Vendor.class), owner = mock(Vendor.class);
        when(attacker.getId()).thenReturn(UUID.randomUUID()); when(owner.getId()).thenReturn(UUID.randomUUID());
        when(vendors.findByUserId(userId)).thenReturn(Optional.of(attacker));
        SubOrder order = new SubOrder(); order.setVendor(owner); when(subOrders.findById(id)).thenReturn(Optional.of(order));
        VendorOrderStatusRequest request = new VendorOrderStatusRequest(); request.setStatus("processing");
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.updateVendorOrderStatus(userId, id, request)).getStatusCode().value());
        verify(subOrders, never()).save(any());
    }

    @Test void changedCouponCannotReplayDifferentCheckout() {
        var service = new CheckoutIdempotencyService(mock(CheckoutIdempotencyRepository.class), JsonMapper.builder().build());
        CheckoutRequest request = new CheckoutRequest(); request.setAddressId(UUID.randomUUID()); request.setDeliveryMethod("delivery"); request.setPaymentMethod("paystack"); request.setCouponCode("AYO10"); UUID user = UUID.randomUUID();
        String first = service.hashRequest(user, request);
        request.setCouponCode("ayo10"); assertEquals(first, service.hashRequest(user, request));
        request.setCouponCode("AYO20"); assertNotEquals(first, service.hashRequest(user, request));
    }
}
