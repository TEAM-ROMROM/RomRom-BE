package com.romrom.romback.global.util;

import java.util.UUID;
import com.github.javafaker.Faker;

public class CommonUtil {
  private static final Faker faker = new Faker();

  public static String getRandomName() {
    return faker.funnyName().name() + "-" + UUID.randomUUID().toString().substring(0, 5);
  }
}
