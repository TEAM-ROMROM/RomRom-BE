package com.romrom.chat.stomp.config;

import static org.springframework.amqp.rabbit.support.micrometer.RabbitTemplateObservation.TemplateLowCardinalityTags.ROUTING_KEY;

import com.romrom.chat.stomp.properties.ChatRoutingProperties;
import com.romrom.chat.stomp.properties.RabbitMqProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableRabbit
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({
    RabbitMqProperties.class,
    ChatRoutingProperties.class
})
public class RabbitMqConfig {

  private final RabbitMqProperties rabbitMqProperties;
  private final ChatRoutingProperties chatRoutingProperties;

  // chat.queue 라는 Queue 생성
  @Bean
  public Queue queue() {
    return new Queue(chatRoutingProperties.chatQueue(), true);
  }

  // AMQP 전략 중 TopicExchange 전략 사용 (chat.exchange 를 이름으로 지정)
  @Bean
  public TopicExchange exchange() {
    return new TopicExchange(chatRoutingProperties.chatExchange());
  }

  // Exchange와 Queue바인딩 ("chat.queue"에 "chat.exchange" 규칙을 Binding)
  @Bean
  public Binding binding(Queue queue, TopicExchange exchange) {
    return BindingBuilder
        .bind(queue)
        .to(exchange)
        .with(ROUTING_KEY);
  }

  // RabbitMQ와의 메시지 통신을 담당하는 클래스
  @Bean
  public RabbitTemplate rabbitTemplate() {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory());
    rabbitTemplate.setMessageConverter(jsonMessageConverter());
    return rabbitTemplate;
  }

  // RabbitMQ와 연결 설정. CachingConnectionFactory를 선택
  @Bean
  public ConnectionFactory connectionFactory() {
    CachingConnectionFactory factory = new CachingConnectionFactory();
    factory.setHost(rabbitMqProperties.host());
    factory.setVirtualHost(rabbitMqProperties.virtualHost());
    factory.setUsername(rabbitMqProperties.username());
    factory.setPassword(rabbitMqProperties.password());
    factory.setPort(rabbitMqProperties.port());
    return factory;
  }

  // 메시지를 JSON형식으로 직렬화하고 역직렬화하는데 사용되는 변환기
  @Bean
  public Jackson2JsonMessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}
