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
