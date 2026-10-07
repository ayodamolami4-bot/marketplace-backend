package com.marketplace.backend;

import com.marketplace.backend.order.Order;
import com.marketplace.backend.order.OrderItem;
import com.marketplace.backend.order.OrderItemRepository;
import com.marketplace.backend.order.OrderRepository;
import com.marketplace.backend.order.OrderStatus;
import com.marketplace.backend.order.SubOrder;
import com.marketplace.backend.order.SubOrderRepository;
import com.marketplace.backend.payment.Payment;
import com.marketplace.backend.payment.PaymentAttempt;
import com.marketplace.backend.payment.PaymentAttemptRepository;
import com.marketplace.backend.payment.PaymentAttemptStatus;
import com.marketplace.backend.payment.PaymentExpiryService;
import com.marketplace.backend.payment.PaymentMethod;
import com.marketplace.backend.payment.PaymentRepository;
import com.marketplace.backend.payment.PaymentRetryResponse;
import com.marketplace.backend.payment.PaymentService;
import com.marketplace.backend.payment.PaymentStatus;
import com.marketplace.backend.payment.paystackclient.PaystackClient;
import com.marketplace.backend.product.Product;
import com.marketplace.backend.product.ProductRepository;
import com.marketplace.backend.user.User;
import com.marketplace.backend.common.DatabaseLockRetryExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MarketplaceBackendApplicationTests {

	@Test
	void failedPaymentReleasesStockAndCancelsOrder() {

		PaymentRepository paymentRepository =
				Mockito.mock(PaymentRepository.class);

		PaymentAttemptRepository paymentAttemptRepository =
				Mockito.mock(PaymentAttemptRepository.class);

		OrderRepository orderRepository =
				Mockito.mock(OrderRepository.class);

		SubOrderRepository subOrderRepository =
				Mockito.mock(SubOrderRepository.class);

		OrderItemRepository orderItemRepository =
				Mockito.mock(OrderItemRepository.class);

		ProductRepository productRepository =
				Mockito.mock(ProductRepository.class);

		PaystackClient paystackClient =
				Mockito.mock(PaystackClient.class);

		PlatformTransactionManager transactionManager =
				Mockito.mock(PlatformTransactionManager.class);

		PaymentService paymentService =
				new PaymentService(
						paymentRepository,
						paymentAttemptRepository,
						orderRepository,
						subOrderRepository,
						orderItemRepository,
						productRepository,
						paystackClient,
						Mockito.mock(JsonMapper.class),
						transactionManager,
						"test-secret"
				);

		UUID orderId =
				UUID.randomUUID();

		UUID paymentId =
				UUID.randomUUID();

		UUID subOrderId =
				UUID.randomUUID();

		UUID productId =
				UUID.randomUUID();

		Order order =
				new Order();

		order.setId(orderId);
		order.setStatus(
				OrderStatus.PENDING_PAYMENT
		);

		Payment payment =
				new Payment();

		payment.setId(paymentId);
		payment.setOrder(order);
		payment.setMethod(
				PaymentMethod.PAYSTACK
		);
		payment.setStatus(
				PaymentStatus.PENDING
		);
		payment.setAmount(
				500_000L
		);
		payment.setTransactionReference(
				"TEST-REF-001"
		);

		SubOrder subOrder =
				new SubOrder();

		subOrder.setId(subOrderId);
		subOrder.setOrder(order);
		subOrder.setStatus(
				OrderStatus.PENDING_PAYMENT
		);

		Product product =
				new Product();

		product.setId(productId);
		product.setStockQuantity(8);

		OrderItem orderItem =
				new OrderItem();

		orderItem.setProduct(product);
		orderItem.setQuantity(2);
		orderItem.setSubOrder(subOrder);

		when(
				paymentRepository
						.findByTransactionReferenceForUpdate(
								"TEST-REF-001"
						)
		).thenReturn(
				Optional.of(payment)
		);

		when(
				orderRepository.findByIdForUpdate(
						orderId
				)
		).thenReturn(
				Optional.of(order)
		);

		when(
				subOrderRepository.findByOrderId(
						orderId
				)
		).thenReturn(
				List.of(subOrder)
		);

		when(
				orderItemRepository.findBySubOrderIdIn(
						List.of(subOrderId)
				)
		).thenReturn(
				List.of(orderItem)
		);

		when(
				productRepository.findByIdForUpdate(
						productId
				)
		).thenReturn(
				product
		);

		paymentService.releaseStockForFailedPayment(
				"TEST-REF-001",
				500_000L,
				"failed"
		);

		assertEquals(
				10,
				product.getStockQuantity()
		);

		assertNotNull(
				order.getStockReleasedAt()
		);

		assertEquals(
				OrderStatus.CANCELLED,
				order.getStatus()
		);

		assertEquals(
				OrderStatus.CANCELLED,
				subOrder.getStatus()
		);

		assertEquals(
				PaymentStatus.FAILED,
				payment.getStatus()
		);

		verify(
				productRepository,
				times(1)
		).findByIdForUpdate(
				productId
		);

		verify(
				orderRepository,
				atLeastOnce()
		).save(order);

		verify(
				paymentRepository,
				atLeastOnce()
		).save(payment);

		verify(
				subOrderRepository,
				atLeastOnce()
		).save(subOrder);
	}

	@Test
	void alreadyReleasedStockIsNotReleasedAgain() {

		PaymentRepository paymentRepository =
				mock(PaymentRepository.class);

		PaymentAttemptRepository paymentAttemptRepository =
				mock(PaymentAttemptRepository.class);

		OrderRepository orderRepository =
				mock(OrderRepository.class);

		SubOrderRepository subOrderRepository =
				mock(SubOrderRepository.class);

		OrderItemRepository orderItemRepository =
				mock(OrderItemRepository.class);

		ProductRepository productRepository =
				mock(ProductRepository.class);

		PaystackClient paystackClient =
				mock(PaystackClient.class);

		PlatformTransactionManager transactionManager =
				mock(PlatformTransactionManager.class);

		PaymentService paymentService =
				new PaymentService(
						paymentRepository,
						paymentAttemptRepository,
						orderRepository,
						subOrderRepository,
						orderItemRepository,
						productRepository,
						paystackClient,
						mock(JsonMapper.class),
						transactionManager,
						"test-secret"
				);

		UUID orderId =
				UUID.randomUUID();

		Order order =
				new Order();

		order.setId(orderId);
		order.setStatus(
				OrderStatus.PENDING_PAYMENT
		);
		order.setStockReleasedAt(
				Instant.now()
		);

		Payment payment =
				new Payment();

		payment.setOrder(order);
		payment.setMethod(
				PaymentMethod.PAYSTACK
		);
		payment.setStatus(
				PaymentStatus.PENDING
		);
		payment.setAmount(
				500_000L
		);
		payment.setTransactionReference(
				"TEST-REF-002"
		);

		when(
				paymentRepository
						.findByTransactionReferenceForUpdate(
								"TEST-REF-002"
						)
		).thenReturn(
				Optional.of(payment)
		);

		when(
				orderRepository.findByIdForUpdate(
						orderId
				)
		).thenReturn(
				Optional.of(order)
		);

		when(
				subOrderRepository.findByOrderId(
						orderId
				)
		).thenReturn(
				List.of()
		);

		paymentService.releaseStockForFailedPayment(
				"TEST-REF-002",
				500_000L,
				"failed"
		);

		assertEquals(
				PaymentStatus.FAILED,
				payment.getStatus()
		);

		assertEquals(
				OrderStatus.CANCELLED,
				order.getStatus()
		);

		verify(
				productRepository,
				never()
		).findByIdForUpdate(
				any()
		);

		verify(
				orderItemRepository,
				never()
		).findBySubOrderIdIn(
				any()
		);
	}

	@Test
	void failedPaystackPaymentCanCreateNewPaymentAttempt() {

		PaymentRepository paymentRepository =
				mock(PaymentRepository.class);

		PaymentAttemptRepository paymentAttemptRepository =
				mock(PaymentAttemptRepository.class);

		OrderRepository orderRepository =
				mock(OrderRepository.class);

		SubOrderRepository subOrderRepository =
				mock(SubOrderRepository.class);

		OrderItemRepository orderItemRepository =
				mock(OrderItemRepository.class);

		ProductRepository productRepository =
				mock(ProductRepository.class);

		PaystackClient paystackClient =
				mock(PaystackClient.class);

		PlatformTransactionManager transactionManager =
				createTransactionManager();

		PaymentService paymentService =
				new PaymentService(
						paymentRepository,
						paymentAttemptRepository,
						orderRepository,
						subOrderRepository,
						orderItemRepository,
						productRepository,
						paystackClient,
						mock(JsonMapper.class),
						transactionManager,
						"test-secret"
				);

		UUID userId =
				UUID.randomUUID();

		UUID orderId =
				UUID.randomUUID();

		UUID paymentId =
				UUID.randomUUID();

		User user =
				mock(User.class);

		when(user.getId())
				.thenReturn(userId);

		when(user.getEmail())
				.thenReturn(
						"customer@example.com"
				);

		Order order =
				new Order();

		order.setId(orderId);
		order.setOrderNumber(
				"ORD-1001"
		);
		order.setStatus(
				OrderStatus.PENDING_PAYMENT
		);
		order.setUser(user);

		Payment payment =
				new Payment();

		payment.setId(paymentId);
		payment.setOrder(order);
		payment.setMethod(
				PaymentMethod.PAYSTACK
		);
		payment.setStatus(
				PaymentStatus.FAILED
		);
		payment.setAmount(
				500_000L
		);
		payment.setTransactionReference(
				"OLD-REFERENCE"
		);

		AtomicReference<PaymentAttempt>
				createdAttempt =
				new AtomicReference<>();

		when(
				orderRepository.findByIdForUpdate(
						orderId
				)
		).thenReturn(
				Optional.of(order)
		);

		when(
				paymentRepository.findByOrderId(
						orderId
				)
		).thenReturn(
				Optional.of(payment)
		);

		when(
				paymentRepository.findByIdForUpdate(
						paymentId
				)
		).thenReturn(
				Optional.of(payment)
		);

		when(
				paymentAttemptRepository
						.findMaxAttemptNumber(
								paymentId
						)
		).thenReturn(1);

		when(
				paymentAttemptRepository.save(
						any(PaymentAttempt.class)
				)
		).thenAnswer(
				invocation -> {

					PaymentAttempt attempt =
							invocation.getArgument(0);

					if (attempt.getId() == null) {

						attempt.setId(
								UUID.randomUUID()
						);
					}

					createdAttempt.set(
							attempt
					);

					return attempt;
				}
		);

		when(
				paymentAttemptRepository
						.findByReferenceForUpdate(
								anyString()
						)
		).thenAnswer(
				invocation ->
						Optional.ofNullable(
								createdAttempt.get()
						)
		);

		when(
				paystackClient.initializeTransaction(
						eq("customer@example.com"),
						eq(500_000L),
						anyString(),
						anyMap()
				)
		).thenAnswer(
				invocation -> {

					String reference =
							invocation.getArgument(2);

					return new PaystackClient.InitializationResult(
							"https://checkout.paystack.com/test",
							reference
					);
				}
		);

		PaymentRetryResponse response =
				paymentService
						.retryPaystackPayment(
								orderId,
								userId
						);

		assertEquals(
				"https://checkout.paystack.com/test",
				response.authorizationUrl()
		);

		assertNotEquals(
				"OLD-REFERENCE",
				response.reference()
		);

		assertTrue(
				response.reference()
						.contains("-A2-")
		);

		PaymentAttempt attempt =
				createdAttempt.get();

		assertNotNull(attempt);

		assertEquals(
				2,
				attempt.getAttemptNumber()
		);

		assertEquals(
				PaymentAttemptStatus.PENDING,
				attempt.getStatus()
		);

		assertEquals(
				PaymentStatus.PENDING,
				payment.getStatus()
		);

		assertEquals(
				response.reference(),
				payment.getTransactionReference()
		);

		verifyNoInteractions(
				productRepository
		);

		verifyNoInteractions(
				orderItemRepository
		);
	}

	@Test
	void successfulPaymentCannotBeRetried() {

		PaymentRepository paymentRepository =
				mock(PaymentRepository.class);

		PaymentAttemptRepository paymentAttemptRepository =
				mock(PaymentAttemptRepository.class);

		OrderRepository orderRepository =
				mock(OrderRepository.class);

		SubOrderRepository subOrderRepository =
				mock(SubOrderRepository.class);

		OrderItemRepository orderItemRepository =
				mock(OrderItemRepository.class);

		ProductRepository productRepository =
				mock(ProductRepository.class);

		PaystackClient paystackClient =
				mock(PaystackClient.class);

		PaymentService paymentService =
				new PaymentService(
						paymentRepository,
						paymentAttemptRepository,
						orderRepository,
						subOrderRepository,
						orderItemRepository,
						productRepository,
						paystackClient,
						mock(JsonMapper.class),
						createTransactionManager(),
						"test-secret"
				);

		UUID userId =
				UUID.randomUUID();

		UUID orderId =
				UUID.randomUUID();

		UUID paymentId =
				UUID.randomUUID();

		User user =
				mock(User.class);

		when(user.getId())
				.thenReturn(userId);

		Order order =
				new Order();

		order.setId(orderId);
		order.setStatus(
				OrderStatus.PENDING_PAYMENT
		);
		order.setUser(user);

		Payment payment =
				new Payment();

		payment.setId(paymentId);
		payment.setOrder(order);
		payment.setMethod(
				PaymentMethod.PAYSTACK
		);
		payment.setStatus(
				PaymentStatus.SUCCESS
		);
		payment.setAmount(
				500_000L
		);

		when(
				orderRepository.findByIdForUpdate(
						orderId
				)
		).thenReturn(
				Optional.of(order)
		);

		when(
				paymentRepository.findByOrderId(
						orderId
				)
		).thenReturn(
				Optional.of(payment)
		);

		when(
				paymentRepository.findByIdForUpdate(
						paymentId
				)
		).thenReturn(
				Optional.of(payment)
		);

		ResponseStatusException exception =
				assertThrows(
						ResponseStatusException.class,
						() ->
								paymentService
										.retryPaystackPayment(
												orderId,
												userId
										)
				);

		assertEquals(
				HttpStatus.CONFLICT,
				exception.getStatusCode()
		);

		verifyNoInteractions(
				paystackClient
		);
	}

	@Test
	void anotherCustomerCannotRetryOrderPayment() {

		PaymentRepository paymentRepository =
				mock(PaymentRepository.class);

		PaymentAttemptRepository paymentAttemptRepository =
				mock(PaymentAttemptRepository.class);

		OrderRepository orderRepository =
				mock(OrderRepository.class);

		SubOrderRepository subOrderRepository =
				mock(SubOrderRepository.class);

		OrderItemRepository orderItemRepository =
				mock(OrderItemRepository.class);

		ProductRepository productRepository =
				mock(ProductRepository.class);

		PaystackClient paystackClient =
				mock(PaystackClient.class);

		PaymentService paymentService =
				new PaymentService(
						paymentRepository,
						paymentAttemptRepository,
						orderRepository,
						subOrderRepository,
						orderItemRepository,
						productRepository,
						paystackClient,
						mock(JsonMapper.class),
						createTransactionManager(),
						"test-secret"
				);

		UUID ownerId =
				UUID.randomUUID();

		UUID attackerId =
				UUID.randomUUID();

		UUID orderId =
				UUID.randomUUID();

		User owner =
				mock(User.class);

		when(owner.getId())
				.thenReturn(ownerId);

		Order order =
				new Order();

		order.setId(orderId);
		order.setStatus(
				OrderStatus.PENDING_PAYMENT
		);
		order.setUser(owner);

		when(
				orderRepository.findByIdForUpdate(
						orderId
				)
		).thenReturn(
				Optional.of(order)
		);

		ResponseStatusException exception =
				assertThrows(
						ResponseStatusException.class,
						() ->
								paymentService
										.retryPaystackPayment(
												orderId,
												attackerId
										)
				);

		assertEquals(
				HttpStatus.FORBIDDEN,
				exception.getStatusCode()
		);

		verifyNoInteractions(
				paystackClient
		);
	}

	@Test
	void activePaymentAttemptPreventsImmediateDuplicateRetry() {

		PaymentRepository paymentRepository =
				mock(PaymentRepository.class);

		PaymentAttemptRepository paymentAttemptRepository =
				mock(PaymentAttemptRepository.class);

		OrderRepository orderRepository =
				mock(OrderRepository.class);

		SubOrderRepository subOrderRepository =
				mock(SubOrderRepository.class);

		OrderItemRepository orderItemRepository =
				mock(OrderItemRepository.class);

		ProductRepository productRepository =
				mock(ProductRepository.class);

		PaystackClient paystackClient =
				mock(PaystackClient.class);

		PaymentService paymentService =
				new PaymentService(
						paymentRepository,
						paymentAttemptRepository,
						orderRepository,
						subOrderRepository,
						orderItemRepository,
						productRepository,
						paystackClient,
						mock(JsonMapper.class),
						createTransactionManager(),
						"test-secret"
				);

		UUID userId =
				UUID.randomUUID();

		UUID orderId =
				UUID.randomUUID();

		UUID paymentId =
				UUID.randomUUID();

		User user =
				mock(User.class);

		when(user.getId())
				.thenReturn(userId);

		when(user.getEmail())
				.thenReturn(
						"customer@example.com"
				);

		Order order =
				new Order();

		order.setId(orderId);
		order.setOrderNumber(
				"ORD-2001"
		);
		order.setStatus(
				OrderStatus.PENDING_PAYMENT
		);
		order.setUser(user);

		Payment payment =
				new Payment();

		payment.setId(paymentId);
		payment.setOrder(order);
		payment.setMethod(
				PaymentMethod.PAYSTACK
		);
		payment.setStatus(
				PaymentStatus.FAILED
		);
		payment.setAmount(
				500_000L
		);
		payment.setTransactionReference(
				"OLD-REFERENCE"
		);

		AtomicReference<PaymentAttempt>
				createdAttempt =
				new AtomicReference<>();

		when(
				orderRepository.findByIdForUpdate(
						orderId
				)
		).thenReturn(
				Optional.of(order)
		);

		when(
				paymentRepository.findByOrderId(
						orderId
				)
		).thenReturn(
				Optional.of(payment)
		);

		when(
				paymentRepository.findByIdForUpdate(
						paymentId
				)
		).thenReturn(
				Optional.of(payment)
		);

		when(
				paymentAttemptRepository
						.findMaxAttemptNumber(
								paymentId
						)
		).thenReturn(1);

		when(
				paymentAttemptRepository.save(
						any(PaymentAttempt.class)
				)
		).thenAnswer(
				invocation -> {

					PaymentAttempt attempt =
							invocation.getArgument(0);

					if (attempt.getId() == null) {

						attempt.setId(
								UUID.randomUUID()
						);
					}

					createdAttempt.set(
							attempt
					);

					return attempt;
				}
		);

		when(
				paymentAttemptRepository
						.findByReferenceForUpdate(
								anyString()
						)
		).thenAnswer(
				invocation ->
						Optional.ofNullable(
								createdAttempt.get()
						)
		);

		when(
				paymentAttemptRepository
						.findByPaymentIdOrderByAttemptNumberDesc(
								paymentId
						)
		).thenAnswer(
				invocation -> {

					PaymentAttempt attempt =
							createdAttempt.get();

					if (attempt == null) {
						return List.of();
					}

					return List.of(attempt);
				}
		);

		when(
				paystackClient.initializeTransaction(
						eq("customer@example.com"),
						eq(500_000L),
						anyString(),
						anyMap()
				)
		).thenAnswer(
				invocation -> {

					String reference =
							invocation.getArgument(2);

					return new PaystackClient.InitializationResult(
							"https://checkout.paystack.com/test",
							reference
					);
				}
		);

		paymentService.retryPaystackPayment(
				orderId,
				userId
		);

		assertEquals(
				PaymentStatus.PENDING,
				payment.getStatus()
		);

		ResponseStatusException exception =
				assertThrows(
						ResponseStatusException.class,
						() ->
								paymentService
										.retryPaystackPayment(
												orderId,
												userId
										)
				);

		assertEquals(
				HttpStatus.CONFLICT,
				exception.getStatusCode()
		);

		verify(
				paystackClient,
				times(1)
		).initializeTransaction(
				anyString(),
				anyLong(),
				anyString(),
				anyMap()
		);
	}
	@Test
	void staleInitiatedPaymentAttemptCanBeRecovered() {

		PaymentRepository paymentRepository =
				mock(PaymentRepository.class);

		PaymentAttemptRepository paymentAttemptRepository =
				mock(PaymentAttemptRepository.class);

		OrderRepository orderRepository =
				mock(OrderRepository.class);

		SubOrderRepository subOrderRepository =
				mock(SubOrderRepository.class);

		OrderItemRepository orderItemRepository =
				mock(OrderItemRepository.class);

		ProductRepository productRepository =
				mock(ProductRepository.class);

		PaystackClient paystackClient =
				mock(PaystackClient.class);

		PaymentService paymentService =
				new PaymentService(
						paymentRepository,
						paymentAttemptRepository,
						orderRepository,
						subOrderRepository,
						orderItemRepository,
						productRepository,
						paystackClient,
						mock(JsonMapper.class),
						createTransactionManager(),
						"test-secret"
				);

		UUID userId =
				UUID.randomUUID();

		UUID orderId =
				UUID.randomUUID();

		UUID paymentId =
				UUID.randomUUID();

		UUID attemptId =
				UUID.randomUUID();

		String reference =
				"RECOVERY-REF-001";

		User user =
				mock(User.class);

		when(
				user.getId()
		).thenReturn(
				userId
		);

		when(
				user.getEmail()
		).thenReturn(
				"customer@example.com"
		);

		Order order =
				new Order();

		order.setId(
				orderId
		);

		order.setStatus(
				OrderStatus.PENDING_PAYMENT
		);

		order.setUser(
				user
		);

		Payment payment =
				new Payment();

		payment.setId(
				paymentId
		);

		payment.setOrder(
				order
		);

		payment.setMethod(
				PaymentMethod.PAYSTACK
		);

		payment.setStatus(
				PaymentStatus.PROCESSING
		);

		payment.setAmount(
				500_000L
		);

		payment.setTransactionReference(
				reference
		);

		PaymentAttempt attempt =
				new PaymentAttempt();

		attempt.setId(
				attemptId
		);

		attempt.setPayment(
				payment
		);

		attempt.setAttemptNumber(
				1
		);

		attempt.setProvider(
				"PAYSTACK"
		);

		attempt.setReference(
				reference
		);

		attempt.setAmount(
				500_000L
		);

		attempt.setStatus(
				PaymentAttemptStatus.INITIATED
		);

		attempt.setInitiatedAt(
				Instant.now()
						.minusSeconds(300)
		);

		when(
				paymentAttemptRepository
						.findByReferenceForUpdate(
								reference
						)
		).thenReturn(
				Optional.of(attempt)
		);

		when(
				paymentRepository
						.findByIdForUpdate(
								paymentId
						)
		).thenReturn(
				Optional.of(payment)
		);

		when(
				orderRepository
						.findByIdForUpdate(
								orderId
						)
		).thenReturn(
				Optional.of(order)
		);

		when(
				paystackClient.initializeTransaction(
						eq("customer@example.com"),
						eq(500_000L),
						eq(reference),
						anyMap()
				)
		).thenReturn(
				new PaystackClient.InitializationResult(
						"https://checkout.paystack.com/recovery",
						reference
				)
		);

		boolean recovered =
				paymentService
						.recoverInitiatedPaystackAttempt(
								reference
						);

		assertTrue(
				recovered
		);

		assertEquals(
				PaymentAttemptStatus.PENDING,
				attempt.getStatus()
		);

		assertEquals(
				PaymentStatus.PENDING,
				payment.getStatus()
		);

		assertNull(
				attempt.getLastError()
		);

		verify(
				paystackClient,
				times(1)
		).initializeTransaction(
				eq("customer@example.com"),
				eq(500_000L),
				eq(reference),
				anyMap()
		);
	}


	@Test
	void expiredFailedPaymentRestoresStockAndCancelsOrder() {

		PaymentExpiryFixture fixture =
				createExpiryFixture();

		Instant cutoff =
				Instant.now()
						.minusSeconds(60);

		fixture.attempt.setFailedAt(
				cutoff.minusSeconds(60)
		);

		when(
				fixture.paymentAttemptRepository
						.findByStatusAndFailedAtLessThanEqualOrderByFailedAtAsc(
								eq(PaymentAttemptStatus.FAILED),
								eq(cutoff),
								any(Pageable.class)
						)
		).thenReturn(
				List.of(fixture.attempt)
		);

		when(
				fixture.paymentAttemptRepository
						.findByReferenceForUpdate(
								fixture.reference
						)
		).thenReturn(
				Optional.of(fixture.attempt)
		);

		when(
				fixture.paymentRepository
						.findByIdForUpdate(
								fixture.paymentId
						)
		).thenReturn(
				Optional.of(fixture.payment)
		);

		when(
				fixture.orderRepository
						.findByIdForUpdate(
								fixture.orderId
						)
		).thenReturn(
				Optional.of(fixture.order)
		);

		when(
				fixture.subOrderRepository
						.findByOrderId(
								fixture.orderId
						)
		).thenReturn(
				List.of(fixture.subOrder)
		);

		when(
				fixture.orderItemRepository
						.findBySubOrderIdIn(
								List.of(
										fixture.subOrderId
								)
						)
		).thenReturn(
				List.of(fixture.orderItem)
		);

		when(
				fixture.productRepository
						.findByIdForUpdate(
								fixture.productId
						)
		).thenReturn(
				fixture.product
		);

		when(
				fixture.paymentAttemptRepository
						.findByPaymentIdOrderByAttemptNumberDesc(
								fixture.paymentId
						)
		).thenReturn(
				List.of(fixture.attempt)
		);

		int expired =
				fixture.paymentExpiryService
						.expireDuePayments(
								cutoff,
								100
						);

		assertEquals(
				1,
				expired
		);

		assertEquals(
				10,
				fixture.product
						.getStockQuantity()
		);

		assertNotNull(
				fixture.order
						.getStockReleasedAt()
		);

		assertEquals(
				OrderStatus.CANCELLED,
				fixture.order.getStatus()
		);

		assertEquals(
				OrderStatus.CANCELLED,
				fixture.subOrder.getStatus()
		);

		verify(
				fixture.productRepository,
				times(1)
		).findByIdForUpdate(
				fixture.productId
		);
	}

	@Test
	void recoveryTimeoutLeavesAttemptUncertain() {

		PaymentRepository paymentRepository =
				mock(PaymentRepository.class);

		PaymentAttemptRepository paymentAttemptRepository =
				mock(PaymentAttemptRepository.class);

		OrderRepository orderRepository =
				mock(OrderRepository.class);

		SubOrderRepository subOrderRepository =
				mock(SubOrderRepository.class);

		OrderItemRepository orderItemRepository =
				mock(OrderItemRepository.class);

		ProductRepository productRepository =
				mock(ProductRepository.class);

		PaystackClient paystackClient =
				mock(PaystackClient.class);

		PaymentService paymentService =
				new PaymentService(
						paymentRepository,
						paymentAttemptRepository,
						orderRepository,
						subOrderRepository,
						orderItemRepository,
						productRepository,
						paystackClient,
						mock(JsonMapper.class),
						createTransactionManager(),
						"test-secret"
				);

		UUID orderId =
				UUID.randomUUID();

		UUID paymentId =
				UUID.randomUUID();

		String reference =
				"TIMEOUT-REF-001";

		User user =
				mock(User.class);

		when(
				user.getEmail()
		).thenReturn(
				"customer@example.com"
		);

		Order order =
				new Order();

		order.setId(
				orderId
		);

		order.setStatus(
				OrderStatus.PENDING_PAYMENT
		);

		order.setUser(
				user
		);

		Payment payment =
				new Payment();

		payment.setId(
				paymentId
		);

		payment.setOrder(
				order
		);

		payment.setMethod(
				PaymentMethod.PAYSTACK
		);

		payment.setStatus(
				PaymentStatus.PROCESSING
		);

		payment.setAmount(
				500_000L
		);

		payment.setTransactionReference(
				reference
		);

		PaymentAttempt attempt =
				new PaymentAttempt();

		attempt.setId(
				UUID.randomUUID()
		);

		attempt.setPayment(
				payment
		);

		attempt.setAttemptNumber(
				1
		);

		attempt.setProvider(
				"PAYSTACK"
		);

		attempt.setReference(
				reference
		);

		attempt.setAmount(
				500_000L
		);

		attempt.setStatus(
				PaymentAttemptStatus.INITIATED
		);

		attempt.setInitiatedAt(
				Instant.now()
						.minusSeconds(300)
		);

		when(
				paymentAttemptRepository
						.findByReferenceForUpdate(
								reference
						)
		).thenReturn(
				Optional.of(attempt)
		);

		when(
				paymentRepository
						.findByIdForUpdate(
								paymentId
						)
		).thenReturn(
				Optional.of(payment)
		);

		when(
				orderRepository
						.findByIdForUpdate(
								orderId
						)
		).thenReturn(
				Optional.of(order)
		);

		when(
				paystackClient.initializeTransaction(
						anyString(),
						anyLong(),
						anyString(),
						anyMap()
				)
		).thenThrow(
				new RuntimeException(
						"Network timeout"
				)
		);

		boolean recovered =
				paymentService
						.recoverInitiatedPaystackAttempt(
								reference
						);

		assertFalse(
				recovered
		);

		/*
		 * Critical production behavior:
		 *
		 * timeout != confirmed payment failure
		 */
		assertEquals(
				PaymentAttemptStatus.INITIATED,
				attempt.getStatus()
		);

		assertEquals(
				PaymentStatus.PROCESSING,
				payment.getStatus()
		);

		assertEquals(
				"Network timeout",
				attempt.getLastError()
		);

		assertNull(
				order.getStockReleasedAt()
		);

		assertEquals(
				OrderStatus.PENDING_PAYMENT,
				order.getStatus()
		);

		verifyNoInteractions(
				productRepository
		);

		verifyNoInteractions(
				orderItemRepository
		);
	}

	@Test
	void oldInitiatedAttemptCannotBeRecoveredAfterNewerRetryExists() {

		PaymentRepository paymentRepository =
				mock(PaymentRepository.class);

		PaymentAttemptRepository paymentAttemptRepository =
				mock(PaymentAttemptRepository.class);

		OrderRepository orderRepository =
				mock(OrderRepository.class);

		SubOrderRepository subOrderRepository =
				mock(SubOrderRepository.class);

		OrderItemRepository orderItemRepository =
				mock(OrderItemRepository.class);

		ProductRepository productRepository =
				mock(ProductRepository.class);

		PaystackClient paystackClient =
				mock(PaystackClient.class);

		PaymentService paymentService =
				new PaymentService(
						paymentRepository,
						paymentAttemptRepository,
						orderRepository,
						subOrderRepository,
						orderItemRepository,
						productRepository,
						paystackClient,
						mock(JsonMapper.class),
						createTransactionManager(),
						"test-secret"
				);

		UUID orderId =
				UUID.randomUUID();

		UUID paymentId =
				UUID.randomUUID();

		String oldReference =
				"OLD-ATTEMPT-REF";

		String currentReference =
				"NEW-ATTEMPT-REF";

		User user =
				mock(User.class);

		when(
				user.getEmail()
		).thenReturn(
				"customer@example.com"
		);

		Order order =
				new Order();

		order.setId(
				orderId
		);

		order.setStatus(
				OrderStatus.PENDING_PAYMENT
		);

		order.setUser(
				user
		);

		Payment payment =
				new Payment();

		payment.setId(
				paymentId
		);

		payment.setOrder(
				order
		);

		payment.setMethod(
				PaymentMethod.PAYSTACK
		);

		payment.setStatus(
				PaymentStatus.PROCESSING
		);

		/*
		 * A newer payment attempt is now current.
		 */
		payment.setTransactionReference(
				currentReference
		);

		PaymentAttempt oldAttempt =
				new PaymentAttempt();

		oldAttempt.setId(
				UUID.randomUUID()
		);

		oldAttempt.setPayment(
				payment
		);

		oldAttempt.setAttemptNumber(
				1
		);

		oldAttempt.setProvider(
				"PAYSTACK"
		);

		oldAttempt.setReference(
				oldReference
		);

		oldAttempt.setAmount(
				500_000L
		);

		oldAttempt.setStatus(
				PaymentAttemptStatus.INITIATED
		);

		oldAttempt.setInitiatedAt(
				Instant.now()
						.minusSeconds(300)
		);

		when(
				paymentAttemptRepository
						.findByReferenceForUpdate(
								oldReference
						)
		).thenReturn(
				Optional.of(oldAttempt)
		);

		when(
				paymentRepository
						.findByIdForUpdate(
								paymentId
						)
		).thenReturn(
				Optional.of(payment)
		);

		boolean recovered =
				paymentService
						.recoverInitiatedPaystackAttempt(
								oldReference
						);

		assertFalse(
				recovered
		);

		assertEquals(
				PaymentAttemptStatus.INITIATED,
				oldAttempt.getStatus()
		);

		assertEquals(
				currentReference,
				payment.getTransactionReference()
		);

		verifyNoInteractions(
				paystackClient
		);

		verify(
				orderRepository,
				never()
		).findByIdForUpdate(
				any()
		);
	}

	@Test
	void expiryDoesNotReleaseStockTwice() {

		PaymentExpiryFixture fixture =
				createExpiryFixture();

		Instant cutoff =
				Instant.now()
						.minusSeconds(60);

		fixture.attempt.setFailedAt(
				cutoff.minusSeconds(60)
		);

		fixture.order.setStockReleasedAt(
				Instant.now()
						.minusSeconds(30)
		);

		when(
				fixture.paymentAttemptRepository
						.findByStatusAndFailedAtLessThanEqualOrderByFailedAtAsc(
								eq(PaymentAttemptStatus.FAILED),
								eq(cutoff),
								any(Pageable.class)
						)
		).thenReturn(
				List.of(fixture.attempt)
		);

		when(
				fixture.paymentAttemptRepository
						.findByReferenceForUpdate(
								fixture.reference
						)
		).thenReturn(
				Optional.of(fixture.attempt)
		);

		when(
				fixture.paymentRepository
						.findByIdForUpdate(
								fixture.paymentId
						)
		).thenReturn(
				Optional.of(fixture.payment)
		);

		when(
				fixture.orderRepository
						.findByIdForUpdate(
								fixture.orderId
						)
		).thenReturn(
				Optional.of(fixture.order)
		);

		int expired =
				fixture.paymentExpiryService
						.expireDuePayments(
								cutoff,
								100
						);

		assertEquals(
				0,
				expired
		);

		assertEquals(
				8,
				fixture.product
						.getStockQuantity()
		);

		verify(
				fixture.productRepository,
				never()
		).findByIdForUpdate(
				any()
		);
	}

	@Test
	void successfulPaymentCannotExpire() {

		PaymentExpiryFixture fixture =
				createExpiryFixture();

		Instant cutoff =
				Instant.now()
						.minusSeconds(60);

		fixture.attempt.setFailedAt(
				cutoff.minusSeconds(60)
		);

		fixture.payment.setStatus(
				PaymentStatus.SUCCESS
		);

		when(
				fixture.paymentAttemptRepository
						.findByStatusAndFailedAtLessThanEqualOrderByFailedAtAsc(
								eq(PaymentAttemptStatus.FAILED),
								eq(cutoff),
								any(Pageable.class)
						)
		).thenReturn(
				List.of(fixture.attempt)
		);

		when(
				fixture.paymentAttemptRepository
						.findByReferenceForUpdate(
								fixture.reference
						)
		).thenReturn(
				Optional.of(fixture.attempt)
		);

		when(
				fixture.paymentRepository
						.findByIdForUpdate(
								fixture.paymentId
						)
		).thenReturn(
				Optional.of(fixture.payment)
		);

		int expired =
				fixture.paymentExpiryService
						.expireDuePayments(
								cutoff,
								100
						);

		assertEquals(
				0,
				expired
		);

		assertEquals(
				OrderStatus.PENDING_PAYMENT,
				fixture.order.getStatus()
		);

		assertNull(
				fixture.order
						.getStockReleasedAt()
		);

		verify(
				fixture.orderRepository,
				never()
		).findByIdForUpdate(
				any()
		);
	}

	@Test
	void activePaymentCannotExpire() {

		PaymentExpiryFixture fixture =
				createExpiryFixture();

		Instant cutoff =
				Instant.now()
						.minusSeconds(60);

		fixture.attempt.setFailedAt(
				cutoff.minusSeconds(60)
		);

		fixture.payment.setStatus(
				PaymentStatus.PROCESSING
		);

		when(
				fixture.paymentAttemptRepository
						.findByStatusAndFailedAtLessThanEqualOrderByFailedAtAsc(
								eq(PaymentAttemptStatus.FAILED),
								eq(cutoff),
								any(Pageable.class)
						)
		).thenReturn(
				List.of(fixture.attempt)
		);

		when(
				fixture.paymentAttemptRepository
						.findByReferenceForUpdate(
								fixture.reference
						)
		).thenReturn(
				Optional.of(fixture.attempt)
		);

		when(
				fixture.paymentRepository
						.findByIdForUpdate(
								fixture.paymentId
						)
		).thenReturn(
				Optional.of(fixture.payment)
		);

		int expired =
				fixture.paymentExpiryService
						.expireDuePayments(
								cutoff,
								100
						);

		assertEquals(
				0,
				expired
		);

		assertNull(
				fixture.order
						.getStockReleasedAt()
		);

		verify(
				fixture.productRepository,
				never()
		).findByIdForUpdate(
				any()
		);
	}

	@Test
	void expiryCancelsPendingSubOrders() {

		PaymentExpiryFixture fixture =
				createExpiryFixture();

		Instant cutoff =
				Instant.now()
						.minusSeconds(60);

		fixture.attempt.setFailedAt(
				cutoff.minusSeconds(60)
		);

		when(
				fixture.paymentAttemptRepository
						.findByStatusAndFailedAtLessThanEqualOrderByFailedAtAsc(
								eq(PaymentAttemptStatus.FAILED),
								eq(cutoff),
								any(Pageable.class)
						)
		).thenReturn(
				List.of(fixture.attempt)
		);

		when(
				fixture.paymentAttemptRepository
						.findByReferenceForUpdate(
								fixture.reference
						)
		).thenReturn(
				Optional.of(fixture.attempt)
		);

		when(
				fixture.paymentRepository
						.findByIdForUpdate(
								fixture.paymentId
						)
		).thenReturn(
				Optional.of(fixture.payment)
		);

		when(
				fixture.orderRepository
						.findByIdForUpdate(
								fixture.orderId
						)
		).thenReturn(
				Optional.of(fixture.order)
		);

		when(
				fixture.subOrderRepository
						.findByOrderId(
								fixture.orderId
						)
		).thenReturn(
				List.of(fixture.subOrder)
		);

		when(
				fixture.orderItemRepository
						.findBySubOrderIdIn(
								List.of(
										fixture.subOrderId
								)
						)
		).thenReturn(
				List.of()
		);

		when(
				fixture.paymentAttemptRepository
						.findByPaymentIdOrderByAttemptNumberDesc(
								fixture.paymentId
						)
		).thenReturn(
				List.of(fixture.attempt)
		);

		int expired =
				fixture.paymentExpiryService
						.expireDuePayments(
								cutoff,
								100
						);

		assertEquals(
				1,
				expired
		);

		assertEquals(
				OrderStatus.CANCELLED,
				fixture.subOrder.getStatus()
		);

		verify(
				fixture.subOrderRepository,
				times(1)
		).save(
				fixture.subOrder
		);
	}

	@Test
	void expiryMarksUnresolvedAttemptsExpired() {

		PaymentExpiryFixture fixture =
				createExpiryFixture();

		Instant cutoff =
				Instant.now()
						.minusSeconds(60);

		fixture.attempt.setFailedAt(
				cutoff.minusSeconds(60)
		);

		PaymentAttempt unresolved =
				new PaymentAttempt();

		unresolved.setId(
				UUID.randomUUID()
		);

		unresolved.setPayment(
				fixture.payment
		);

		unresolved.setAttemptNumber(2);

		unresolved.setProvider(
				"PAYSTACK"
		);

		unresolved.setReference(
				"UNRESOLVED-REF"
		);

		unresolved.setAmount(
				500_000L
		);

		unresolved.setStatus(
				PaymentAttemptStatus.PENDING
		);

		unresolved.setInitiatedAt(
				Instant.now()
						.minusSeconds(300)
		);

		when(
				fixture.paymentAttemptRepository
						.findByStatusAndFailedAtLessThanEqualOrderByFailedAtAsc(
								eq(PaymentAttemptStatus.FAILED),
								eq(cutoff),
								any(Pageable.class)
						)
		).thenReturn(
				List.of(fixture.attempt)
		);

		when(
				fixture.paymentAttemptRepository
						.findByReferenceForUpdate(
								fixture.reference
						)
		).thenReturn(
				Optional.of(fixture.attempt)
		);

		when(
				fixture.paymentRepository
						.findByIdForUpdate(
								fixture.paymentId
						)
		).thenReturn(
				Optional.of(fixture.payment)
		);

		when(
				fixture.orderRepository
						.findByIdForUpdate(
								fixture.orderId
						)
		).thenReturn(
				Optional.of(fixture.order)
		);

		when(
				fixture.subOrderRepository
						.findByOrderId(
								fixture.orderId
						)
		).thenReturn(
				List.of()
		);

		when(
				fixture.paymentAttemptRepository
						.findByPaymentIdOrderByAttemptNumberDesc(
								fixture.paymentId
						)
		).thenReturn(
				List.of(
						unresolved,
						fixture.attempt
				)
		);

		int expired =
				fixture.paymentExpiryService
						.expireDuePayments(
								cutoff,
								100
						);

		assertEquals(
				1,
				expired
		);

		assertEquals(
				PaymentAttemptStatus.EXPIRED,
				unresolved.getStatus()
		);

		assertEquals(
				"Payment window expired",
				unresolved.getLastError()
		);

		assertEquals(
				PaymentAttemptStatus.FAILED,
				fixture.attempt.getStatus()
		);

		verify(
				fixture.paymentAttemptRepository,
				atLeastOnce()
		).save(
				unresolved
		);
	}

	private static PaymentExpiryFixture
	createExpiryFixture() {

		PaymentRepository paymentRepository =
				mock(PaymentRepository.class);

		PaymentAttemptRepository paymentAttemptRepository =
				mock(PaymentAttemptRepository.class);

		OrderRepository orderRepository =
				mock(OrderRepository.class);

		SubOrderRepository subOrderRepository =
				mock(SubOrderRepository.class);

		OrderItemRepository orderItemRepository =
				mock(OrderItemRepository.class);

		ProductRepository productRepository =
				mock(ProductRepository.class);

		PlatformTransactionManager transactionManager =
				createTransactionManager();

		DatabaseLockRetryExecutor lockRetryExecutor =
				new DatabaseLockRetryExecutor(
						transactionManager,
						3,
						0,
						0
				);

		PaymentExpiryService paymentExpiryService =
				new PaymentExpiryService(
						paymentRepository,
						paymentAttemptRepository,
						orderRepository,
						subOrderRepository,
						orderItemRepository,
						productRepository,
						lockRetryExecutor
				);
		UUID orderId =
				UUID.randomUUID();

		UUID paymentId =
				UUID.randomUUID();

		UUID attemptId =
				UUID.randomUUID();

		UUID subOrderId =
				UUID.randomUUID();

		UUID productId =
				UUID.randomUUID();

		String reference =
				"FAILED-ATTEMPT-001";

		Order order =
				new Order();

		order.setId(orderId);
		order.setStatus(
				OrderStatus.PENDING_PAYMENT
		);

		Payment payment =
				new Payment();

		payment.setId(paymentId);
		payment.setOrder(order);
		payment.setMethod(
				PaymentMethod.PAYSTACK
		);
		payment.setStatus(
				PaymentStatus.FAILED
		);
		payment.setAmount(
				500_000L
		);
		payment.setTransactionReference(
				reference
		);

		PaymentAttempt attempt =
				new PaymentAttempt();

		attempt.setId(attemptId);
		attempt.setPayment(payment);
		attempt.setAttemptNumber(1);
		attempt.setProvider(
				"PAYSTACK"
		);
		attempt.setReference(
				reference
		);
		attempt.setAmount(
				500_000L
		);
		attempt.setStatus(
				PaymentAttemptStatus.FAILED
		);
		attempt.setInitiatedAt(
				Instant.now()
						.minusSeconds(600)
		);

		SubOrder subOrder =
				new SubOrder();

		subOrder.setId(subOrderId);
		subOrder.setOrder(order);
		subOrder.setStatus(
				OrderStatus.PENDING_PAYMENT
		);

		Product product =
				new Product();

		product.setId(productId);
		product.setStockQuantity(8);

		OrderItem orderItem =
				new OrderItem();

		orderItem.setProduct(product);
		orderItem.setSubOrder(subOrder);
		orderItem.setQuantity(2);

		return new PaymentExpiryFixture(
				paymentExpiryService,
				paymentRepository,
				paymentAttemptRepository,
				orderRepository,
				subOrderRepository,
				orderItemRepository,
				productRepository,
				orderId,
				paymentId,
				subOrderId,
				productId,
				reference,
				order,
				payment,
				attempt,
				subOrder,
				product,
				orderItem
		);
	}

	private static PlatformTransactionManager
	createTransactionManager() {

		PlatformTransactionManager transactionManager =
				mock(
						PlatformTransactionManager.class
				);

		when(
				transactionManager.getTransaction(
						any(
								TransactionDefinition.class
						)
				)
		).thenAnswer(
				invocation ->
						new SimpleTransactionStatus()
		);

		return transactionManager;
	}

	private record PaymentExpiryFixture(
			PaymentExpiryService paymentExpiryService,
			PaymentRepository paymentRepository,
			PaymentAttemptRepository paymentAttemptRepository,
			OrderRepository orderRepository,
			SubOrderRepository subOrderRepository,
			OrderItemRepository orderItemRepository,
			ProductRepository productRepository,
			UUID orderId,
			UUID paymentId,
			UUID subOrderId,
			UUID productId,
			String reference,
			Order order,
			Payment payment,
			PaymentAttempt attempt,
			SubOrder subOrder,
			Product product,
			OrderItem orderItem
	) {
	}
}