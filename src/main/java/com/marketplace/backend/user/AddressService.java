package com.marketplace.backend.user;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressService(
            AddressRepository addressRepository,
            UserRepository userRepository
    ) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getUserAddresses(UUID userId) {
        return addressRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AddressResponse createAddress(
            UUID userId,
            AddressRequest request
    ) {
        User user = getUser(userId);

        if (request.defaultAddress()) {
            clearDefaultAddress(userId);
        }

        Address address = new Address();
        address.setUser(user);
        applyRequest(address, request);

        return toResponse(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse updateAddress(
            UUID userId,
            UUID addressId,
            AddressRequest request
    ) {
        Address address = getOwnedAddress(userId, addressId);

        if (request.defaultAddress()) {
            clearDefaultAddress(userId);
        }

        applyRequest(address, request);

        return toResponse(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(
            UUID userId,
            UUID addressId
    ) {
        Address address = getOwnedAddress(userId, addressId);
        addressRepository.delete(address);
    }

    private void applyRequest(
            Address address,
            AddressRequest request
    ) {
        address.setRecipientName(request.recipientName().trim());
        address.setPhoneNumber(request.phoneNumber().trim());
        address.setAddressLine(request.addressLine().trim());
        address.setCity(request.city().trim());
        address.setState(request.state().trim());
        address.setCountry(request.country().trim());

        address.setPostalCode(
                request.postalCode() == null || request.postalCode().isBlank()
                        ? null
                        : request.postalCode().trim()
        );

        address.setDefaultAddress(request.defaultAddress());
    }

    private void clearDefaultAddress(UUID userId) {
        addressRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .forEach(address -> address.setDefaultAddress(false));
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));
    }

    private Address getOwnedAddress(
            UUID userId,
            UUID addressId
    ) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Address not found"
                ));

        if (!address.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Address not found"
            );
        }

        return address;
    }

    private AddressResponse toResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getRecipientName(),
                address.getPhoneNumber(),
                address.getAddressLine(),
                address.getCity(),
                address.getState(),
                address.getCountry(),
                address.getPostalCode(),
                address.isDefaultAddress(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }
}