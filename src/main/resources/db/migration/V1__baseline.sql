--
-- PostgreSQL database dump
--


-- Dumped from database version 18.6
-- Dumped by pg_dump version 18.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA IF NOT EXISTS public;


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: addresses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.addresses (
    id uuid NOT NULL,
    address_line character varying(255) NOT NULL,
    city character varying(100) NOT NULL,
    country character varying(100) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    default_address boolean NOT NULL,
    phone_number character varying(30) NOT NULL,
    postal_code character varying(20),
    recipient_name character varying(100) NOT NULL,
    state character varying(100) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: cart_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cart_items (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    quantity integer NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    product_id uuid NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categories (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    description character varying(500),
    name character varying(100) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    parent_id uuid
);


--
-- Name: coupons; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.coupons (
    id uuid NOT NULL,
    code character varying(50) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    discount_percent integer NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    vendor_id uuid NOT NULL
);


--
-- Name: delivery_tracking_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.delivery_tracking_events (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    description character varying(500),
    status character varying(30) NOT NULL,
    sub_order_id uuid NOT NULL,
    CONSTRAINT delivery_tracking_events_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'SHIPPED'::character varying, 'IN_TRANSIT'::character varying, 'OUT_FOR_DELIVERY'::character varying, 'DELIVERED'::character varying, 'FAILED'::character varying, 'RETURNED'::character varying])::text[])))
);


--
-- Name: delivery_zones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.delivery_zones (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    name character varying(100) NOT NULL,
    shipping_fee bigint NOT NULL,
    state character varying(100) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    message character varying(1000) NOT NULL,
    read boolean NOT NULL,
    title character varying(150) NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: order_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_items (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    product_name character varying(200) NOT NULL,
    quantity integer NOT NULL,
    subtotal bigint NOT NULL,
    unit_price bigint NOT NULL,
    product_id uuid NOT NULL,
    sub_order_id uuid NOT NULL
);


--
-- Name: orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orders (
    id uuid NOT NULL,
    address_line character varying(255) NOT NULL,
    city character varying(100) NOT NULL,
    country character varying(100) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    discount_amount bigint NOT NULL,
    order_number character varying(30) NOT NULL,
    phone_number character varying(30) NOT NULL,
    postal_code character varying(20),
    recipient_name character varying(100) NOT NULL,
    shipping_fee bigint NOT NULL,
    state character varying(100) NOT NULL,
    status character varying(30) NOT NULL,
    subtotal bigint NOT NULL,
    total_amount bigint NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    user_id uuid NOT NULL,
    delivery_method character varying(50),
    CONSTRAINT orders_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING_PAYMENT'::character varying, 'CONFIRMED'::character varying, 'PROCESSING'::character varying, 'SHIPPED'::character varying, 'DELIVERED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payments (
    id uuid NOT NULL,
    amount bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    method character varying(30) NOT NULL,
    paid_at timestamp(6) with time zone,
    status character varying(20) NOT NULL,
    transaction_reference character varying(100),
    updated_at timestamp(6) with time zone NOT NULL,
    order_id uuid NOT NULL,
    CONSTRAINT payments_method_check CHECK (((method)::text = ANY ((ARRAY['PAYSTACK'::character varying, 'CASH_ON_DELIVERY'::character varying])::text[]))),
    CONSTRAINT payments_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'SUCCESS'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: pickup_locations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pickup_locations (
    id uuid NOT NULL,
    active boolean NOT NULL,
    address character varying(255) NOT NULL,
    city character varying(100) NOT NULL,
    country character varying(100) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    name character varying(150) NOT NULL,
    state character varying(100) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);


--
-- Name: product_images; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.product_images (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    display_order integer NOT NULL,
    image_url character varying(1000) NOT NULL,
    public_id character varying(255) NOT NULL,
    product_id uuid NOT NULL
);


--
-- Name: products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.products (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    description character varying(2000),
    name character varying(200) NOT NULL,
    price bigint NOT NULL,
    rejection_reason character varying(1000),
    status character varying(20) NOT NULL,
    stock_quantity integer NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    category_id uuid NOT NULL,
    vendor_id uuid NOT NULL,
    CONSTRAINT products_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'SUSPENDED'::character varying])::text[])))
);


--
-- Name: reviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reviews (
    id uuid NOT NULL,
    comment character varying(2000),
    created_at timestamp(6) with time zone NOT NULL,
    rating integer NOT NULL,
    rejection_reason character varying(1000),
    status character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    product_id uuid NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT reviews_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'HIDDEN'::character varying])::text[])))
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id uuid NOT NULL,
    name character varying(20) NOT NULL,
    CONSTRAINT roles_name_check CHECK (((name)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'VENDOR'::character varying, 'ADMIN'::character varying])::text[])))
);


--
-- Name: sub_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sub_orders (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    discount_amount bigint NOT NULL,
    shipping_fee bigint NOT NULL,
    status character varying(30) NOT NULL,
    sub_order_number character varying(40) NOT NULL,
    subtotal bigint NOT NULL,
    total_amount bigint NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    order_id uuid NOT NULL,
    vendor_id uuid NOT NULL,
    CONSTRAINT sub_orders_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING_PAYMENT'::character varying, 'PENDING_FULFILLMENT'::character varying, 'CONFIRMED'::character varying, 'PROCESSING'::character varying, 'SHIPPED'::character varying, 'DELIVERED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    role_id uuid NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    email character varying(320) NOT NULL,
    email_verified boolean NOT NULL,
    name character varying(150) NOT NULL,
    password_hash character varying(255) NOT NULL,
    status character varying(20) NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT users_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying, 'LOCKED'::character varying])::text[])))
);


--
-- Name: vendor_finance_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vendor_finance_configs (
    id uuid NOT NULL,
    commission_percent integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    payout_schedule character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    vendor_id uuid NOT NULL,
    CONSTRAINT vendor_finance_configs_payout_schedule_check CHECK (((payout_schedule)::text = ANY ((ARRAY['MANUAL'::character varying, 'DAILY'::character varying, 'WEEKLY'::character varying, 'MONTHLY'::character varying])::text[])))
);


--
-- Name: vendor_payouts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vendor_payouts (
    id uuid NOT NULL,
    amount bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    paid_at timestamp(6) with time zone,
    reference character varying(100),
    status character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    vendor_id uuid NOT NULL,
    CONSTRAINT vendor_payouts_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'PAID'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: vendors; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vendors (
    id uuid NOT NULL,
    business_description character varying(1000),
    business_name character varying(150) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    paystack_subaccount_code character varying(100),
    status character varying(20) NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT vendors_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'SUSPENDED'::character varying])::text[])))
);


--
-- Name: wishlist_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wishlist_items (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    product_id uuid NOT NULL,
    wishlist_id uuid NOT NULL
);


--
-- Name: wishlists; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wishlists (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: addresses addresses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.addresses
    ADD CONSTRAINT addresses_pkey PRIMARY KEY (id);


--
-- Name: cart_items cart_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT cart_items_pkey PRIMARY KEY (id);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


--
-- Name: coupons coupons_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.coupons
    ADD CONSTRAINT coupons_pkey PRIMARY KEY (id);


--
-- Name: delivery_tracking_events delivery_tracking_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_tracking_events
    ADD CONSTRAINT delivery_tracking_events_pkey PRIMARY KEY (id);


--
-- Name: delivery_zones delivery_zones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_zones
    ADD CONSTRAINT delivery_zones_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: order_items order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (id);


--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);


--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);


--
-- Name: pickup_locations pickup_locations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pickup_locations
    ADD CONSTRAINT pickup_locations_pkey PRIMARY KEY (id);


--
-- Name: product_images product_images_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_images
    ADD CONSTRAINT product_images_pkey PRIMARY KEY (id);


--
-- Name: products products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);


--
-- Name: reviews reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_pkey PRIMARY KEY (id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: sub_orders sub_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sub_orders
    ADD CONSTRAINT sub_orders_pkey PRIMARY KEY (id);


--
-- Name: sub_orders uk1lmiax0oo7xx3iqet7jajspwt; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sub_orders
    ADD CONSTRAINT uk1lmiax0oo7xx3iqet7jajspwt UNIQUE (sub_order_number);


--
-- Name: payments uk8vo36cen604as7etdfwmyjsxt; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT uk8vo36cen604as7etdfwmyjsxt UNIQUE (order_id);


--
-- Name: cart_items uk_cart_items_user_product; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT uk_cart_items_user_product UNIQUE (user_id, product_id);


--
-- Name: categories uk_categories_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT uk_categories_name UNIQUE (name);


--
-- Name: coupons uk_coupons_vendor_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.coupons
    ADD CONSTRAINT uk_coupons_vendor_code UNIQUE (vendor_id, code);


--
-- Name: delivery_zones uk_delivery_zones_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_zones
    ADD CONSTRAINT uk_delivery_zones_name UNIQUE (name);


--
-- Name: reviews uk_reviews_user_product; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT uk_reviews_user_product UNIQUE (user_id, product_id);


--
-- Name: roles uk_roles_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT uk_roles_name UNIQUE (name);


--
-- Name: users uk_users_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_users_email UNIQUE (email);


--
-- Name: vendor_finance_configs uk_vendor_finance_config_vendor_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendor_finance_configs
    ADD CONSTRAINT uk_vendor_finance_config_vendor_id UNIQUE (vendor_id);


--
-- Name: vendors uk_vendors_user_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendors
    ADD CONSTRAINT uk_vendors_user_id UNIQUE (user_id);


--
-- Name: wishlist_items uk_wishlist_items_wishlist_product; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wishlist_items
    ADD CONSTRAINT uk_wishlist_items_wishlist_product UNIQUE (wishlist_id, product_id);


--
-- Name: wishlists uk_wishlists_user_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wishlists
    ADD CONSTRAINT uk_wishlists_user_id UNIQUE (user_id);


--
-- Name: orders uknthkiu7pgmnqnu86i2jyoe2v7; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT uknthkiu7pgmnqnu86i2jyoe2v7 UNIQUE (order_number);


--
-- Name: payments ukrwn36natqiwaseu5c3jvaun3; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT ukrwn36natqiwaseu5c3jvaun3 UNIQUE (transaction_reference);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (role_id, user_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: vendor_finance_configs vendor_finance_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendor_finance_configs
    ADD CONSTRAINT vendor_finance_configs_pkey PRIMARY KEY (id);


--
-- Name: vendor_payouts vendor_payouts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendor_payouts
    ADD CONSTRAINT vendor_payouts_pkey PRIMARY KEY (id);


--
-- Name: vendors vendors_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendors
    ADD CONSTRAINT vendors_pkey PRIMARY KEY (id);


--
-- Name: wishlist_items wishlist_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wishlist_items
    ADD CONSTRAINT wishlist_items_pkey PRIMARY KEY (id);


--
-- Name: wishlists wishlists_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wishlists
    ADD CONSTRAINT wishlists_pkey PRIMARY KEY (id);


--
-- Name: idx_addresses_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_addresses_user_id ON public.addresses USING btree (user_id);


--
-- Name: idx_cart_items_product_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cart_items_product_id ON public.cart_items USING btree (product_id);


--
-- Name: idx_cart_items_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cart_items_user_id ON public.cart_items USING btree (user_id);


--
-- Name: idx_categories_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_categories_name ON public.categories USING btree (name);


--
-- Name: idx_categories_parent_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_categories_parent_id ON public.categories USING btree (parent_id);


--
-- Name: idx_coupons_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_coupons_expires_at ON public.coupons USING btree (expires_at);


--
-- Name: idx_coupons_vendor_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_coupons_vendor_id ON public.coupons USING btree (vendor_id);


--
-- Name: idx_delivery_events_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_events_created_at ON public.delivery_tracking_events USING btree (created_at);


--
-- Name: idx_delivery_events_sub_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_events_sub_order_id ON public.delivery_tracking_events USING btree (sub_order_id);


--
-- Name: idx_notifications_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_created_at ON public.notifications USING btree (created_at);


--
-- Name: idx_notifications_read; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_read ON public.notifications USING btree (read);


--
-- Name: idx_notifications_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_user_id ON public.notifications USING btree (user_id);


--
-- Name: idx_order_items_product_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_items_product_id ON public.order_items USING btree (product_id);


--
-- Name: idx_order_items_sub_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_items_sub_order_id ON public.order_items USING btree (sub_order_id);


--
-- Name: idx_orders_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_status ON public.orders USING btree (status);


--
-- Name: idx_orders_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_user_id ON public.orders USING btree (user_id);


--
-- Name: idx_payments_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_order_id ON public.payments USING btree (order_id);


--
-- Name: idx_payments_reference; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_reference ON public.payments USING btree (transaction_reference);


--
-- Name: idx_payments_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_status ON public.payments USING btree (status);


--
-- Name: idx_pickup_locations_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pickup_locations_active ON public.pickup_locations USING btree (active);


--
-- Name: idx_product_images_product_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_product_images_product_id ON public.product_images USING btree (product_id);


--
-- Name: idx_products_category_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_category_id ON public.products USING btree (category_id);


--
-- Name: idx_products_vendor_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_vendor_id ON public.products USING btree (vendor_id);


--
-- Name: idx_reviews_product_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reviews_product_id ON public.reviews USING btree (product_id);


--
-- Name: idx_reviews_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reviews_status ON public.reviews USING btree (status);


--
-- Name: idx_reviews_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reviews_user_id ON public.reviews USING btree (user_id);


--
-- Name: idx_sub_orders_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sub_orders_order_id ON public.sub_orders USING btree (order_id);


--
-- Name: idx_sub_orders_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sub_orders_status ON public.sub_orders USING btree (status);


--
-- Name: idx_sub_orders_vendor_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sub_orders_vendor_id ON public.sub_orders USING btree (vendor_id);


--
-- Name: idx_vendor_payouts_reference; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vendor_payouts_reference ON public.vendor_payouts USING btree (reference);


--
-- Name: idx_vendor_payouts_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vendor_payouts_status ON public.vendor_payouts USING btree (status);


--
-- Name: idx_vendor_payouts_vendor_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vendor_payouts_vendor_id ON public.vendor_payouts USING btree (vendor_id);


--
-- Name: idx_wishlist_items_product_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wishlist_items_product_id ON public.wishlist_items USING btree (product_id);


--
-- Name: addresses fk1fa36y2oqhao3wgg2rw1pi459; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.addresses
    ADD CONSTRAINT fk1fa36y2oqhao3wgg2rw1pi459 FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: cart_items fk1re40cjegsfvw58xrkdp6bac6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT fk1re40cjegsfvw58xrkdp6bac6 FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: orders fk32ql8ubntj5uh44ph9659tiih; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fk32ql8ubntj5uh44ph9659tiih FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: wishlists fk330pyw2el06fn5g28ypyljt16; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wishlists
    ADD CONSTRAINT fk330pyw2el06fn5g28ypyljt16 FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: delivery_tracking_events fk4kxofn8k1ljay27thj6gjp0k; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_tracking_events
    ADD CONSTRAINT fk4kxofn8k1ljay27thj6gjp0k FOREIGN KEY (sub_order_id) REFERENCES public.sub_orders(id);


--
-- Name: order_items fk5dv8t9ns7l4he5eqdos8idf4x; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fk5dv8t9ns7l4he5eqdos8idf4x FOREIGN KEY (sub_order_id) REFERENCES public.sub_orders(id);


--
-- Name: cart_items fk709eickf3kc0dujx3ub9i7btf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT fk709eickf3kc0dujx3ub9i7btf FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: payments fk81gagumt0r8y3rmudcgpbk42l; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT fk81gagumt0r8y3rmudcgpbk42l FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: notifications fk9y21adhxn0ayjhfocscqox7bh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT fk9y21adhxn0ayjhfocscqox7bh FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: reviews fkcgy7qjc1r99dp117y9en6lxye; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT fkcgy7qjc1r99dp117y9en6lxye FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: sub_orders fkcwqdpqgm2o3ffe8ioqvb69vgd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sub_orders
    ADD CONSTRAINT fkcwqdpqgm2o3ffe8ioqvb69vgd FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: coupons fkeo53jdsgc5htpc5wuiyd4vkt6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.coupons
    ADD CONSTRAINT fkeo53jdsgc5htpc5wuiyd4vkt6 FOREIGN KEY (vendor_id) REFERENCES public.vendors(id);


--
-- Name: vendor_finance_configs fkerl4aw6k7rciih3l1qy4s0ppf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendor_finance_configs
    ADD CONSTRAINT fkerl4aw6k7rciih3l1qy4s0ppf FOREIGN KEY (vendor_id) REFERENCES public.vendors(id);


--
-- Name: user_roles fkh8ciramu9cc9q3qcqiv4ue8a6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fkh8ciramu9cc9q3qcqiv4ue8a6 FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- Name: user_roles fkhfh9dx7w3ubf1co1vdev94g3f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fkhfh9dx7w3ubf1co1vdev94g3f FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: vendors fkiuqso7j7nivq7sb3v3v4j7ein; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendors
    ADD CONSTRAINT fkiuqso7j7nivq7sb3v3v4j7ein FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: sub_orders fkk3qdiwpw0fyj6x1dk2l2ikin0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sub_orders
    ADD CONSTRAINT fkk3qdiwpw0fyj6x1dk2l2ikin0 FOREIGN KEY (vendor_id) REFERENCES public.vendors(id);


--
-- Name: wishlist_items fkkem9l8vd14pk3cc4elnpl0n00; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wishlist_items
    ADD CONSTRAINT fkkem9l8vd14pk3cc4elnpl0n00 FOREIGN KEY (wishlist_id) REFERENCES public.wishlists(id);


--
-- Name: vendor_payouts fkmugulu8p827i90o7w5vevew2j; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendor_payouts
    ADD CONSTRAINT fkmugulu8p827i90o7w5vevew2j FOREIGN KEY (vendor_id) REFERENCES public.vendors(id);


--
-- Name: order_items fkocimc7dtr037rh4ls4l95nlfi; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fkocimc7dtr037rh4ls4l95nlfi FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: products fkog2rp4qthbtt2lfyhfo32lsw9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT fkog2rp4qthbtt2lfyhfo32lsw9 FOREIGN KEY (category_id) REFERENCES public.categories(id);


--
-- Name: reviews fkpl51cejpw4gy5swfar8br9ngi; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT fkpl51cejpw4gy5swfar8br9ngi FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: product_images fkqnq71xsohugpqwf3c9gxmsuy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_images
    ADD CONSTRAINT fkqnq71xsohugpqwf3c9gxmsuy FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: wishlist_items fkqxj7lncd242b59fb78rqegyxj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wishlist_items
    ADD CONSTRAINT fkqxj7lncd242b59fb78rqegyxj FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: products fks6kdu75k7ub4s95ydsr52p59s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT fks6kdu75k7ub4s95ydsr52p59s FOREIGN KEY (vendor_id) REFERENCES public.vendors(id);


--
-- Name: categories fksaok720gsu4u2wrgbk10b5n8d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT fksaok720gsu4u2wrgbk10b5n8d FOREIGN KEY (parent_id) REFERENCES public.categories(id);


--
-- PostgreSQL database dump complete
--


