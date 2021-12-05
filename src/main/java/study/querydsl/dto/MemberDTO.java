package study.querydsl.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MemberDTO {
    private String username;
    private int age;

    @QueryProjection // DTO도 Q파일로 생성된다.
    public MemberDTO(String username, int age) {
        this.username = username;
        this.age = age;
    }
}
