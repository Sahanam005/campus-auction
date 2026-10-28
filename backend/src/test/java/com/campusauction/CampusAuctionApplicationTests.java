package com.campusauction;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "campus-auction.image-storage-dir=${java.io.tmpdir}/campus-auction-test-images")
@AutoConfigureMockMvc
class CampusAuctionApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void contextLoads() {
	}

	@Test
	@Transactional
	void marketplaceEndpointsSupportAuctionAndBidFlow() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.database").value("campus_auction"));

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
				"sellerName", "milestone-test-seller");
		String createdAuction = mockMvc.perform(post("/api/auctions")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(auctionRequest)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode auction = objectMapper.readTree(createdAuction);
		long auctionId = auction.get("id").asLong();

		Map<String, Object> bidRequest = Map.of(
				"bidderName", "milestone-test-bidder",
				"amount", new BigDecimal("10.00"));
		mockMvc.perform(post("/api/auctions/{id}/bids", auctionId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(bidRequest)))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/auctions/{id}/bids", auctionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].bidderName").value("milestone-test-bidder"))
				.andExpect(jsonPath("$[0].amount").value(10.00))
				.andExpect(jsonPath("$[0].auction").doesNotExist());

		Map<String, Object> invalidBidRequest = Map.of(
				"bidderName", "milestone-test-bidder",
				"amount", new BigDecimal("10.50"));
		mockMvc.perform(post("/api/auctions/{id}/bids", auctionId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(invalidBidRequest)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").exists());
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
		Map<String, Object> auctionRequest = Map.of(
				"title", "Image upload test auction",
				"category", "Books",
				"conditionLabel", "Good",
				"description", "Verifies uploaded image storage and retrieval",
				"startingPrice", new BigDecimal("10.00"),
				"minIncrement", new BigDecimal("1.00"),
				"closingTime", LocalDateTime.now().plusHours(1),
				"sellerName", "image-test-seller");
		MockMultipartFile auctionPart = new MockMultipartFile(
				"auction", "auction.json", MediaType.APPLICATION_JSON_VALUE,
				objectMapper.writeValueAsBytes(auctionRequest));
		byte[] imageBytes = createPngImage();
		MockMultipartFile imagePart = new MockMultipartFile(
				"image", "item.png", MediaType.IMAGE_PNG_VALUE, imageBytes);

		String response = mockMvc.perform(multipart("/api/auctions")
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

	private byte[] createPngImage() throws IOException {
		BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		return output.toByteArray();
	}

}
