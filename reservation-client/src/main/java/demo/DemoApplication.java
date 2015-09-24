package demo;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@EnableFeignClients
@EnableCircuitBreaker
@EnableZuulProxy
@EnableDiscoveryClient
@SpringBootApplication
public class DemoApplication {

    @Bean
    CommandLineRunner dc(DiscoveryClient dc) {
        return args -> dc.getInstances("reservation-service")
                .forEach(si -> System.out.println(String.format("%s %s:%s", si.getServiceId(), si.getHost(), si.getPort())));
    }

    @Bean
    CommandLineRunner rt(RestTemplate rt) {
        return args -> {

            ParameterizedTypeReference<List<Reservation>> ptr =
                    new ParameterizedTypeReference<List<Reservation>>() {};

            ResponseEntity<List<Reservation>> response =
                    rt.exchange("http://reservation-service/reservations",
                        HttpMethod.GET,
                        null,
                        ptr);

            response.getBody().forEach(System.out::println);
        };
    }

    @Bean
    CommandLineRunner fc(ReservationClient rc) {
        return args -> rc.getReservations().forEach(System.out::println);
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@FeignClient("reservation-service")
interface ReservationClient {
    @RequestMapping(method = RequestMethod.GET)//, value="/reservations")
    Collection<Reservation> getReservations();
}

@Component
class ReservationIntegration {
    @Autowired
    ReservationClient rc;

    public Collection<String> reservationNamesFallback() {
        return Collections.emptyList();
    }

    @HystrixCommand(fallbackMethod = "reservationNamesFallback")
    public Collection<String> reservationNames() {
        return rc.getReservations().stream()
                .map(Reservation::getReservationName)
                .collect(Collectors.toList());
    }
}

@RestController
class ReservationNamesRestController {

    @Autowired
    ReservationIntegration ri;

    @RequestMapping("/names")
    Collection<String> reservationNames() {
        return ri.reservationNames();
    }
}

class Reservation {
    private Long id;
    private String reservationName;

    public Reservation() {
    }

    @Override
    public String toString() {
        return "Reservation{" +
                "id=" + id +
                ", reservationName='" + reservationName + '\'' +
                '}';
    }

    public Long getId() {
        return id;
    }

    public String getReservationName() {
        return reservationName;
    }
}
