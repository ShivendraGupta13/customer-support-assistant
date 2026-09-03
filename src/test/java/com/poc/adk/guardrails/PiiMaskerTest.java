package com.poc.adk.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiMaskerTest {

  @Test
  void masksPlaybookCardNumber() {
    String masked =
        PiiMasker.mask(
            "My card number is 4111-1111-1111-1111, can you note that on my account?");

    assertThat(masked).doesNotContain("4111-1111-1111-1111");
    assertThat(masked).contains("****");
  }

  @Test
  void detectsCardNumber() {
    assertThat(PiiMasker.containsPii("My card number is 4111-1111-1111-1111")).isTrue();
    assertThat(PiiMasker.containsPii("What's the status of ORD-5001?")).isFalse();
  }

  @Test
  void leavesNormalTextUnchanged() {
    String text = "What's the status of ORD-5001?";

    assertThat(PiiMasker.mask(text)).isEqualTo(text);
  }
}
