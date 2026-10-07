package com.marketplace.backend;

import com.marketplace.backend.user.*;
import com.marketplace.backend.coupon.*;
import com.marketplace.backend.cart.CartItemRepository;
import jakarta.validation.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidationAndCouponTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
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
}
