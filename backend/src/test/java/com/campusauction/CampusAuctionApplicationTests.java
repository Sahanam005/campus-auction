package com.campusauction;

import com.campusauction.auth.MarketplaceUser;
import com.campusauction.auth.MarketplaceUserRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "campus-auction.image-storage-dir=${java.io.tmpdir}/campus-auction-test-images")
@AutoConfigureMockMvc
class CampusAuctionApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MarketplaceUserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void contextLoads() {
	}

	@Test
	@Transactional
	void marketplaceEndpointsSupportAuctionAndBidFlow() throws Exception {
		MockHttpSession sellerSession = registerAccount("seller", "Test Seller");
		MockHttpSession bidderSession = registerAccount("bidder", "Test Seller");
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.database").value("campus_auction"));
		mockMvc.perform(get("/api/health").header("Origin", "http://localhost:5173"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
				.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
		mockMvc.perform(options("/api/auth/register")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
				.andExpect(header().string("Access-Control-Allow-Credentials", "true"));

		mockMvc.perform(get("/api/auctions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());

		Map<String, Object> auctionRequest = Map.of(
				"title", "Milestone 1 test auction",
				"category", "Books",
				"conditionLabel", "Good",
				"description", "Transaction-scoped endpoint test",
				"startingPrice", new BigDecimal("10.00"),
				"minIncrement", new BigDecimal("1.00"),
				"closingTime", LocalDateTime.now().plusHours(1),
				"sellerName", "forged-seller-name");
		String createdAuction = mockMvc.perform(post("/api/auctions")
				.session(sellerSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(auctionRequest)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sellerName").value("Test Seller"))
				.andExpect(jsonPath("$.sellerUserId").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		JsonNode auction = objectMapper.readTree(createdAuction);
		long auctionId = auction.get("id").asLong();

		mockMvc.perform(get("/api/auctions/mine").session(sellerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(auctionId));
		mockMvc.perform(get("/api/auctions/mine").session(bidderSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());

		mockMvc.perform(post("/api/auctions/{id}/bids", auctionId)
				.session(sellerSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("amount", new BigDecimal("10.00")))))
				.andExpect(status().isBadRequest());

		Map<String, Object> bidRequest = Map.of("amount", new BigDecimal("10.00"));
		mockMvc.perform(post("/api/auctions/{id}/bids", auctionId)
				.session(bidderSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(bidRequest)))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/auctions/{id}/bids", auctionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].bidderName").value("Test Seller"))
				.andExpect(jsonPath("$[0].amount").value(10.00))
				.andExpect(jsonPath("$[0].auction").doesNotExist());

		Map<String, Object> invalidBidRequest = Map.of("amount", new BigDecimal("10.50"));
		mockMvc.perform(post("/api/auctions/{id}/bids", auctionId)
				.session(bidderSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(invalidBidRequest)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").exists());
	}

	@Test
	@Transactional
	void registrationLoginAndLogoutUseAHashedPasswordAndSession() throws Exception {
		String email = "account-" + UUID.randomUUID() + "@example.test";
		Map<String, String> registration = Map.of(
				"email", email, "displayName", "Campus Member",
				"password", "correct-horse-battery");

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(registration)))
				.andExpect(status().isForbidden());
		var csrfResult = mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.csrfToken").isNotEmpty())
				.andReturn();
		String csrfToken = objectMapper.readTree(
				csrfResult.getResponse().getContentAsString()).get("csrfToken").asText();
		MockHttpSession csrfSession = (MockHttpSession) csrfResult.getRequest().getSession(false);
		var registrationResult = mockMvc.perform(post("/api/auth/register")
				.session(csrfSession)
				.header("X-CSRF-TOKEN", csrfToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(registration)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andReturn();
		MockHttpSession session = (MockHttpSession) registrationResult.getRequest().getSession(false);
		MarketplaceUser account = userRepository.findByEmail(email).orElseThrow();
		org.junit.jupiter.api.Assertions.assertNotEquals(
				registration.get("password"), account.getPasswordHash());
		org.junit.jupiter.api.Assertions.assertTrue(
				passwordEncoder.matches(registration.get("password"), account.getPasswordHash()));

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Campus Member"));
		mockMvc.perform(post("/api/auth/register")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(registration)))
				.andExpect(status().isConflict());
		mockMvc.perform(post("/api/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"email", email, "password", "incorrect-password"))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Email or password is incorrect"));
		mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isUnauthorized());
		var loginResult = mockMvc.perform(post("/api/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"email", email, "password", registration.get("password")))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Campus Member"))
				.andReturn();
		MockHttpSession loginSession = (MockHttpSession) loginResult.getRequest().getSession(false);
		mockMvc.perform(get("/api/auth/me").session(loginSession))
				.andExpect(status().isOk());
	}

	@Test
	void protectedWriteEndpointsRejectAnonymousRequests() throws Exception {
		mockMvc.perform(get("/api/auctions/mine"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Authentication required"));
		mockMvc.perform(post("/api/auctions")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Authentication required"));
	}

	@Test
	void missingAuctionsReturnNotFound() throws Exception {
		mockMvc.perform(get("/api/auctions/999999999"))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/auctions/999999999/bids"))
				.andExpect(status().isNotFound());
	}

	@Test
	@Transactional
	void uploadedAuctionImageIsStoredAndServed() throws Exception {
		MockHttpSession sellerSession = registerAccount("image-seller", "Image Seller");
		Map<String, Object> auctionRequest = Map.of(
				"title", "Image upload test auction",
				"category", "Books",
				"conditionLabel", "Good",
				"description", "Verifies uploaded image storage and retrieval",
				"startingPrice", new BigDecimal("10.00"),
				"minIncrement", new BigDecimal("1.00"),
				"closingTime", LocalDateTime.now().plusHours(1));
		MockMultipartFile auctionPart = new MockMultipartFile(
				"auction", "auction.json", MediaType.APPLICATION_JSON_VALUE,
				objectMapper.writeValueAsBytes(auctionRequest));
		byte[] imageBytes = createPngImage();
		MockMultipartFile imagePart = new MockMultipartFile(
				"image", "item.png", MediaType.IMAGE_PNG_VALUE, imageBytes);

		String response = mockMvc.perform(multipart("/api/auctions")
				.session(sellerSession)
				.with(csrf())
				.file(auctionPart)
				.file(imagePart))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.imageUrl").isString())
				.andReturn().getResponse().getContentAsString();
		JsonNode savedAuction = objectMapper.readTree(response);
		String imageUrl = savedAuction.get("imageUrl").asText();
		mockMvc.perform(get("/api/auctions/{id}", savedAuction.get("id").asLong()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.imageUrl").value(imageUrl));
		String imageKey = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
		Path storedImage = Path.of(
				System.getProperty("java.io.tmpdir"),
				"campus-auction-test-images", imageKey);
		try {
			byte[] retrievedImage = mockMvc.perform(get(imageUrl))
					.andExpect(status().isOk())
					.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
							.contentTypeCompatibleWith(MediaType.IMAGE_PNG))
					.andReturn().getResponse().getContentAsByteArray();
			org.junit.jupiter.api.Assertions.assertArrayEquals(imageBytes, retrievedImage);
			org.junit.jupiter.api.Assertions.assertTrue(Files.size(storedImage) > 0);
		} finally {
			Files.deleteIfExists(storedImage);
		}
	}

	@Autowired
	private com.campusauction.auction.AuctionRepository auctionRepository;

	@Test
	@Transactional
	void sellerCanEditAndCancelOwnListingAndStrangersAreBlocked() throws Exception {
		MockHttpSession sellerSession = registerAccount("seller-edit", "Seller Owner");
		MockHttpSession otherSession = registerAccount("other-user", "Other User");

		// Create listing
		Map<String, Object> createReq = Map.of(
				"title", "Original Title",
				"category", "Books",
				"conditionLabel", "Good",
				"description", "Original Description",
				"startingPrice", new BigDecimal("100.00"),
				"minIncrement", new BigDecimal("10.00"),
				"closingTime", LocalDateTime.now().plusHours(2));

		String createdStr = mockMvc.perform(post("/api/auctions")
				.session(sellerSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(createReq)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		long auctionId = objectMapper.readTree(createdStr).get("id").asLong();

		// Other user tries to edit -> 403 Forbidden
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/auctions/{id}", auctionId)
				.session(otherSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("title", "Hacked Title"))))
				.andExpect(status().isForbidden());

		// Owner edits listing
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/auctions/{id}", auctionId)
				.session(sellerSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"title", "Updated Title",
						"description", "Updated Description",
						"startingPrice", new BigDecimal("120.00")))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Updated Title"))
				.andExpect(jsonPath("$.startingPrice").value(120.00));

		// Place bid from other user
		mockMvc.perform(post("/api/auctions/{id}/bids", auctionId)
				.session(otherSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("amount", new BigDecimal("120.00")))))
				.andExpect(status().isCreated());

		// Owner cannot change starting price or increment now that bids exist
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/auctions/{id}", auctionId)
				.session(sellerSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("startingPrice", new BigDecimal("150.00")))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Cannot change starting price after bids have been placed"));

		// Other user tries to cancel -> 403 Forbidden
		mockMvc.perform(post("/api/auctions/{id}/cancel", auctionId)
				.session(otherSession)
				.with(csrf()))
				.andExpect(status().isForbidden());

		// Owner cancels
		mockMvc.perform(post("/api/auctions/{id}/cancel", auctionId)
				.session(sellerSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("reason", "Decided to keep it"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		// Bidding on cancelled listing rejected
		mockMvc.perform(post("/api/auctions/{id}/bids", auctionId)
				.session(otherSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("amount", new BigDecimal("130.00")))))
				.andExpect(status().isBadRequest());
	}

	@Test
	@Transactional
	void antiSnipingExtendsClosingTimeWhenBidPlacedInFinal30Seconds() throws Exception {
		MockHttpSession sellerSession = registerAccount("sniping-seller", "Sniping Seller");
		MockHttpSession bidderSession = registerAccount("sniper-bidder", "Sniper Bidder");

		// Listing closing in 15 seconds (within the final 30 seconds)
		LocalDateTime closingSoon = LocalDateTime.now().plusSeconds(15);
		Map<String, Object> createReq = Map.of(
				"title", "Anti-sniping Test Item",
				"category", "Electronics",
				"conditionLabel", "Good",
				"description", "Tests the 30s extension rule",
				"startingPrice", new BigDecimal("50.00"),
				"minIncrement", new BigDecimal("5.00"),
				"closingTime", closingSoon);

		String createdStr = mockMvc.perform(post("/api/auctions")
				.session(sellerSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(createReq)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		long auctionId = objectMapper.readTree(createdStr).get("id").asLong();

		// Bid placed during the final 30 seconds
		mockMvc.perform(post("/api/auctions/{id}/bids", auctionId)
				.session(bidderSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("amount", new BigDecimal("50.00")))))
				.andExpect(status().isCreated());

		// Verify that closing time was extended to ~60s after that bid (definitely > 40s from now)
		String fetchedStr = mockMvc.perform(get("/api/auctions/{id}", auctionId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		LocalDateTime newClosingTime = LocalDateTime.parse(
				objectMapper.readTree(fetchedStr).get("closingTime").asText());

		org.junit.jupiter.api.Assertions.assertTrue(
				newClosingTime.isAfter(LocalDateTime.now().plusSeconds(40)),
				"Closing time should have been extended to 60 seconds after the bid");
	}

	@Test
	@Transactional
	void expiredAuctionsCloseAsUnsoldOrEndedPendingSellerAndFollowDecisionLifecycle() throws Exception {
		MockHttpSession sellerSession = registerAccount("decision-seller", "Decision Seller");
		MockHttpSession bidderSession = registerAccount("winning-bidder", "Winning Bidder");
		MockHttpSession strangerSession = registerAccount("stranger-user", "Stranger");

		// 1. Auction with NO bids -> UNSOLD
		Map<String, Object> noBidsReq = Map.of(
				"title", "Unsold Item",
				"category", "Books",
				"conditionLabel", "Fair",
				"description", "Will get no bids",
				"startingPrice", new BigDecimal("20.00"),
				"minIncrement", new BigDecimal("2.00"),
				"closingTime", LocalDateTime.now().plusSeconds(1));

		String unsoldCreated = mockMvc.perform(post("/api/auctions")
				.session(sellerSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(noBidsReq)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long unsoldId = objectMapper.readTree(unsoldCreated).get("id").asLong();

		// Manually wind closing time back so it expires
		com.campusauction.auction.Auction unsoldAuction = auctionRepository.findById(unsoldId).orElseThrow();
		unsoldAuction.setClosingTime(LocalDateTime.now().minusMinutes(1));
		auctionRepository.save(unsoldAuction);

		// Fetch auction triggers closeIfExpired -> UNSOLD
		mockMvc.perform(get("/api/auctions/{id}", unsoldId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UNSOLD"));

		// 2. Auction WITH bids -> ENDED_PENDING_SELLER, seller approve/reject/handover
		Map<String, Object> withBidsReq = Map.of(
				"title", "Popular Item",
				"category", "Calculators",
				"conditionLabel", "Like New",
				"description", "Will get bids and be approved",
				"startingPrice", new BigDecimal("100.00"),
				"minIncrement", new BigDecimal("10.00"),
				"closingTime", LocalDateTime.now().plusHours(1));

		String withBidsCreated = mockMvc.perform(post("/api/auctions")
				.session(sellerSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(withBidsReq)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long withBidsId = objectMapper.readTree(withBidsCreated).get("id").asLong();

		mockMvc.perform(post("/api/auctions/{id}/bids", withBidsId)
				.session(bidderSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("amount", new BigDecimal("100.00")))))
				.andExpect(status().isCreated());

		// Manually expire it
		com.campusauction.auction.Auction bidAuction = auctionRepository.findById(withBidsId).orElseThrow();
		bidAuction.setClosingTime(LocalDateTime.now().minusMinutes(1));
		auctionRepository.save(bidAuction);

		// Fetch triggers close -> ENDED_PENDING_SELLER
		mockMvc.perform(get("/api/auctions/{id}", withBidsId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ENDED_PENDING_SELLER"))
				.andExpect(jsonPath("$.winningBidderName").value("Winning Bidder"))
				.andExpect(jsonPath("$.winningBidAmount").value(100.00));

		// Stranger cannot approve or reject
		mockMvc.perform(post("/api/auctions/{id}/approve", withBidsId)
				.session(strangerSession)
				.with(csrf()))
				.andExpect(status().isForbidden());

		// Seller approves winner -> RESERVED_PENDING_PICKUP
		mockMvc.perform(post("/api/auctions/{id}/approve", withBidsId)
				.session(sellerSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"pickupLocation", "Main Library Entrance",
						"pickupNotes", "Between 4 and 5 PM"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RESERVED_PENDING_PICKUP"))
				.andExpect(jsonPath("$.pickupLocation").value("Main Library Entrance"));

		// Schedule pickup details
		mockMvc.perform(post("/api/auctions/{id}/pickup", withBidsId)
				.session(bidderSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"pickupLocation", "Student Center Café",
						"pickupNotes", "See you tomorrow!"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pickupLocation").value("Student Center Café"));

		// Confirmed handover -> COMPLETED
		mockMvc.perform(post("/api/auctions/{id}/handover", withBidsId)
				.session(sellerSession)
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.handoverConfirmedAt").isNotEmpty());
	}

	@Test
	@Transactional
	void unownedListingsSafelyRejectEditAndCancel() throws Exception {
		MockHttpSession userSession = registerAccount("regular-user", "Regular User");

		// Create an unowned auction directly in DB
		com.campusauction.auction.Auction unowned = new com.campusauction.auction.Auction();
		unowned.setTitle("Legacy Unowned Item");
		unowned.setCategory("Books");
		unowned.setConditionLabel("Good");
		unowned.setDescription("Has no owner in MySQL");
		unowned.setStartingPrice(new BigDecimal("15.00"));
		unowned.setMinIncrement(new BigDecimal("1.00"));
		unowned.setClosingTime(LocalDateTime.now().plusDays(2));
		unowned.setSellerName("anonymous_legacy");
		unowned.setStatus("ACTIVE");
		com.campusauction.auction.Auction saved = auctionRepository.save(unowned);

		// Public GET works safely
		mockMvc.perform(get("/api/auctions/{id}", saved.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Legacy Unowned Item"))
				.andExpect(jsonPath("$.sellerUserId").doesNotExist());

		// Attempt to edit unowned listing -> 403 Forbidden
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/auctions/{id}", saved.getId())
				.session(userSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("title", "Trying to steal legacy"))))
				.andExpect(status().isForbidden());

		// Attempt to cancel unowned listing -> 403 Forbidden
		mockMvc.perform(post("/api/auctions/{id}/cancel", saved.getId())
				.session(userSession)
				.with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	@Transactional
	void notificationsAndSavedSearchesAndListingQAFlow() throws Exception {
		MockHttpSession sellerSession = registerAccount("qa-seller", "QA Seller");
		MockHttpSession buyerSession = registerAccount("qa-buyer", "QA Buyer");

		// 1. Buyer creates a saved search for Calculators
		mockMvc.perform(post("/api/saved-searches")
				.session(buyerSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"query", "graphing",
						"category", "Calculators",
						"maxPrice", new BigDecimal("500.00")))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.category").value("Calculators"));

		mockMvc.perform(get("/api/saved-searches").session(buyerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].query").value("graphing"));

		// 2. Seller creates an auction matching the saved search
		Map<String, Object> auctionReq = Map.of(
				"title", "TI-84 Graphing Calculator",
				"category", "Calculators",
				"conditionLabel", "Excellent",
				"description", "Graphing tool for calculus",
				"startingPrice", new BigDecimal("350.00"),
				"minIncrement", new BigDecimal("25.00"),
				"closingTime", LocalDateTime.now().plusDays(3));

		String createdStr = mockMvc.perform(post("/api/auctions")
				.session(sellerSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(auctionReq)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long auctionId = objectMapper.readTree(createdStr).get("id").asLong();

		// Buyer should have received a notification about saved search match!
		mockMvc.perform(get("/api/notifications").session(buyerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].title").value("Saved Search Alert"));

		// 3. Buyer asks a question on the listing
		String questionStr = mockMvc.perform(post("/api/auctions/{id}/questions", auctionId)
				.session(buyerSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("questionText", "Does it include batteries?"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.questionText").value("Does it include batteries?"))
				.andReturn().getResponse().getContentAsString();
		long questionId = objectMapper.readTree(questionStr).get("id").asLong();

		// Check public questions
		mockMvc.perform(get("/api/auctions/{id}/questions", auctionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(questionId));

		// Seller answers the question
		mockMvc.perform(post("/api/auctions/{id}/questions/{qid}/answer", auctionId, questionId)
				.session(sellerSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("answerText", "Yes, brand new AAA batteries included!"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answerText").value("Yes, brand new AAA batteries included!"));

		// 4. Buyer places a bid -> seller gets notification
		mockMvc.perform(post("/api/auctions/{id}/bids", auctionId)
				.session(buyerSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("amount", new BigDecimal("350.00")))))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/notifications").session(sellerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].title").value("New bid on your listing"));

		// Mark all notifications read
		mockMvc.perform(post("/api/notifications/mark-all-read")
				.session(sellerSession)
				.with(csrf()))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/notifications/unread-count").session(sellerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unreadCount").value(0));
	}

	@Test
	@Transactional
	void aiAuctionAssistantEnforcesBudgetAndProvidesRealDataAndRules() throws Exception {
		MockHttpSession sellerSession = registerAccount("ai-seller", "AI Seller");

		// Create real active listing in DB
		Map<String, Object> cycleReq = Map.of(
				"title", "Hero Mountain Cycle",
				"category", "Cycles",
				"conditionLabel", "Good",
				"description", "21 gear cycle for campus commute",
				"startingPrice", new BigDecimal("1500.00"),
				"minIncrement", new BigDecimal("100.00"),
				"closingTime", LocalDateTime.now().plusDays(5));

		mockMvc.perform(post("/api/auctions")
				.session(sellerSession)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(cycleReq)))
				.andExpect(status().isCreated());

		// 1. Assistant rules query
		mockMvc.perform(post("/api/assistant/chat")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"message", "Explain the bidding rules and anti-sniping"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.explanation").isNotEmpty())
				.andExpect(jsonPath("$.explanation").value(org.hamcrest.Matchers.containsString("Anti-Sniping Protection")));

		// 2. Search with budget that matches
		mockMvc.perform(post("/api/assistant/chat")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"message", "Find cycles for commute",
						"statedBudget", new BigDecimal("2000.00")))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.listings[0].title").value("Hero Mountain Cycle"));

		// 3. Search with budget that is too low -> does NOT fabricate listings
		mockMvc.perform(post("/api/assistant/chat")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"message", "Find cycles for commute",
						"statedBudget", new BigDecimal("500.00")))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.listings").isEmpty())
				.andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("found no active listings within your stated budget")));

		// 4. Bid request with budget enforcement
		mockMvc.perform(post("/api/assistant/chat")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"message", "I want to bid on the cycle",
						"statedBudget", new BigDecimal("1000.00")))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.proposedAction.withinBudget").value(false))
				.andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("exceeds your stated budget")));

		// 5. Bid proposal with explicit confirmation required
		mockMvc.perform(post("/api/assistant/chat")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"message", "Prepare a bid for the cycle",
						"statedBudget", new BigDecimal("1600.00")))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.proposedAction.withinBudget").value(true))
				.andExpect(jsonPath("$.proposedAction.requiresConfirmation").value(true))
				.andExpect(jsonPath("$.proposedAction.proposedAmount").value(1500.00));
	}



	private MockHttpSession registerAccount(String localPart, String displayName) throws Exception {
		String email = localPart + "-" + UUID.randomUUID() + "@example.test";
		String request = objectMapper.writeValueAsString(Map.of(
				"email", email,
				"displayName", displayName,
				"password", "correct-horse-battery"));
		return (MockHttpSession) mockMvc.perform(post("/api/auth/register")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
				.andExpect(status().isOk())
				.andReturn().getRequest().getSession(false);
	}

	private byte[] createPngImage() throws IOException {
		BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		return output.toByteArray();
	}

}
