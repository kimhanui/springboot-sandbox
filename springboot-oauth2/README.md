# **springboot-oauth2**

소셜로그인을 구현하기 위한 실습입니다. **소셜 로그인 인증, 인가, 사용자의 데이터를 가져오는 흐름**을 이해하기 위해 아래 두가지 케이스를 실습합니다.

- provider를 제공하는 구글 로그인
- provider를 제공하지 않는 카카오 로그인

> 구글 로그인은 이동욱님의 우주책을 참고했고, 카카오 로그인은 잘 정리된 블로그 포스팅들을 참고했습니다. 일부 [http://crguezl.github.io/apuntes-ruby/node768.html](http://crguezl.github.io/apuntes-ruby/node768.html) 를 번역한 부분도 있습니다.

# **1. 구글 로그인 구현하기**

1. **Google Cloud Platform**

> 어떤 소셜 로그인이던 코드만 작성한다고해서 소셜로그인되는게 아닙니다. 각 벤더에 내 어플리케이션(웹앱)을 등록해서
> - 애플리케이션의 고유 id (secret은 벤더에 따라 선택 또는 필수) 발급
> - 동의 항목 구성   
>
> 하는 사전 작업이 필요합니다.

- [Google Cloud Platform](http://localhost:8080/login/oauth2/code/google) 에서 상단바의 프로젝트 선택 드롭다운 버튼을 눌러 새 프로젝트를 생성합니다.

  ![https://user-images.githubusercontent.com/30483337/130709516-132deb1f-1adb-4226-87d6-5dc73b4a1408.png](https://user-images.githubusercontent.com/30483337/130709516-132deb1f-1adb-4226-87d6-5dc73b4a1408.png)

- 메뉴>OAuth 동의 화면 : 앱 이름, 개발자 연락처 정도만 작성하고 민감한 정보 등의 범위는 따로 설정하지 않았습니다.
- 메뉴>사용자 인증 정보 : 상단의 사용자 인증 정보 만들기 -> OAuth 클라이언트 ID만들기 선택
    - 웹 애플리케이션 유형으로 선택, 리디렉션 URI는 `http://localhost:8080/login/oauth2/code/google` 를 작성합니다.

2. 코드 설명

Spring Security의 전반적인 내용은 제외하고 Oauth2설정을 하면서 추가된 내용을 위주로 설명합니다.

- 의존성

    ```groovy
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    ```

  oauth2와 이를 지원해주는 spring security 의존성을 추가합니다.

- `SecurityConfig.java`

    ```java
    // configure 메서드
    .and()
        .oauth2Login()  // oauth2 로그인 기능에 대한 진입점        
            .userInfoEndpoint() // 로그인 성공 이후 사용자 정보 가져올 때의 설정 담당            
                .userService(customOAuth2UserService);  // 로그인 성공 이후 사용될 UserService구현체 등록
    ```

  각 메서드가 진입점(endpoint)이 되기 때문에 indent를 넣었습니다. (style 차이)

- `application-oauth.properties`

    ```groovy
    spring.security.oauth2.client.registration.google.client-id=클라이언트ID
    spring.security.oauth2.client.registration.google.client-secret=클라이언트보안키
    spring.security.oauth2.client.registration.google.scope=profile,email
    ```

- `CustomOAuthUsrService.java`

    ```java
    // loadUser 메서드

    String registrationId = userRequest.getClientRegistration().getRegistrationId(); //(1)
    String userNameAttributeName = userRequest.getClientRegistration()
                    .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName(); //(2)
    OAuthAttributes attributes = OAuthAttributes.
                    of(registrationId, userNameAttributeName, oAuth2User.getAttributes()); //(3)
    User user = saveOrUpdate(attributes); //(4)
    httpSession.setAttribute("user", new SessionUser(user)); //(5)

            return new DefaultOAuth2User(
                    Collections.singleton(new SimpleGrantedAuthority(user.getRoleKey())),
                    attributes.getAttributes(),
                    attributes.getNameAttributeKey());
    ```

  (1) registrationId :  소셜 로그인하는 서비스를 구분하는 코드(ex. kakao, google)

  (2) OAuth2 로그인을 할 때 Primary Key처럼 키가 되는 필드값이고 구글은 `sub` 라는 기본 코드를 지원하지만 네이버, 카카오등은 지원안합니다. (라는데 구글처럼 고정해놓아도 되고, 네이버 카카오처럼 안 써도 되는거면 로그인할 때 어떻게 PK역할을 한다는건지 잘 모르겠다)

  (3) registrationId에 따라 정보 제공하는 `키`값이 다르기 때문에 `OAuthAttributes` 객체에서 내가 얻고자하는 정보들을 필드로 두고 registrationId에 상관없이 얻을 수 있게 합니다.

  (4) 얻은 `OAuthAttributes` 정보로 같은 클래스 내의 `saveOrUpdate` 메서드에서 User 를 조회 후 없으면 저장, 있다면 정보를 업데이트합니다.

  (5) User를 저장 및 업데이트 했으므로 세션에  유저정보를 저장해서 내 서비스 내에서 참고할 수 있게 합니다.

- `IndexController.java`

    ```java
    @GetMapping("/")
    public String index(Model model) {
        SessionUser user = (SessionUser) httpSession.getAttribute("user");
        if(user != null){
            model.addAttribute("name", user.getName());
        }
        return "index";
    }
    ```

  세션에 유저 정보가 저장돼있는지 확인해서 있다면 뷰에 `유저 이름`을 `name` 키에 저장해서 전달합니다.


# **2. 카카오 로그인 구현하기**

1. Kakao Developers에 애플리케이션 등록
- [Kakao Developers](https://developers.kakao.com/) 에서 내 애플리케이션에서 애플리케이션 추가
- 제품설정>카카오 로그인 : 활성화 상태 ON으로 변경, Redirect URI에 `http://localhost:8080/login/oauth2/code/kakao` 추가

  > Redirect URI에 https 설정한 주소라면 http와 별도로 추가해줍니다.

- 제품설정>동의항목 : 닉네임, 프로필 사진, 카카오계정(이메일)에 동의 설정

  > 여기서 보이는 ID값이 scope값이 됩니다.

- 제품설정>보안: Client Secret은 선택입니다. 사용한다면 발급후 활성화까지 합니다.
- 앱설정>플랫폼: Web의 사이트 도메인에 `http://localhost:8080` 를 등록합니다.

  > 여기서도 https를 적용한 https://localhost:8080를 추가할 수 있습니다.

2. 코드 설명
- `application.properties`

    ```groovy

    # 카카오 로그인(REST API 키가 client-id다)
    spring.security.oauth2.client.registration.kakao.client-id= 클라이언트ID
    spring.security.oauth2.client.registration.kakao.redirect-uri=http://localhost:8080/login/oauth2/code/kakao
    spring.security.oauth2.client.registration.kakao.client-authentication-method=POST
    spring.security.oauth2.client.registration.kakao.authorization-grant-type=authorization_code
    spring.security.oauth2.client.registration.kakao.scope=profile_nickname, profile_image, account_email
    spring.security.oauth2.client.registration.kakao.client-name=Kakao
    # 카카오 Provider
    spring.security.oauth2.client.provider.kakao.authorization_uri=https://kauth.kakao.com/oauth/authorize
    spring.security.oauth2.client.provider.kakao.token_uri=https://kauth.kakao.com/oauth/token
    spring.security.oauth2.client.provider.kakao.user-info-uri=https://kapi.kakao.com/v2/user/me
    spring.security.oauth2.client.provider.kakao.user_name_attribute=id
    ```
  
  카카오는 Spring Security Oauth에 url이 바로 제공되지 않으므로 스스로 입력을 해줘야합니다. 다른 예제들을 보면 Provider를 직접 만들기도 하는데 별도의 Provider없이 여기서 입력하는 것만으로도 충분합니다.

- `OAuthAttributes.java`
    ```java
     public static OAuthAttributes of(String registrationId,
                                     String userNameAttributeName,
                                     Map<String, Object> attributes) {
        if("kakao".equals(registrationId)) {
            return ofKakao("id", attributes);
        }
        return ofGoogle(userNameAttributeName, attributes);
    }
    ```
![image](https://user-images.githubusercontent.com/30483337/130626070-cd652ead-40e9-438e-8a31-207e60c4219b.png)
![image](https://s3.us-west-2.amazonaws.com/secure.notion-static.com/6ae9ad2f-63b6-48f7-b44d-46c43458b02d/Untitled.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAT73L2G45O3KS52Y5%2F20210825%2Fus-west-2%2Fs3%2Faws4_request&X-Amz-Date=20210825T090014Z&X-Amz-Expires=86400&X-Amz-Signature=8577df884f6e73a6df45c446b6e521cecee286a9fff85e926bd49df7f2f5ac84&X-Amz-SignedHeaders=host&response-content-disposition=filename%20%3D%22Untitled.png%22)
![image](https://s3.us-west-2.amazonaws.com/secure.notion-static.com/dd08cc36-1f44-4dd7-a6d6-9b7f79383fc6/Untitled.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAT73L2G45O3KS52Y5%2F20210825%2Fus-west-2%2Fs3%2Faws4_request&X-Amz-Date=20210825T090057Z&X-Amz-Expires=86400&X-Amz-Signature=94d9d69d90ad04a5f604b1f0bdd3b42b788ea65770ab4254480ac2b525274ea6&X-Amz-SignedHeaders=host&response-content-disposition=filename%20%3D%22Untitled.png%22)

# **3. 인증 흐름 설명**

> 여기선 [http://crguezl.github.io/apuntes-ruby/node768.html](http://crguezl.github.io/apuntes-ruby/node768.html) 글의 일부를 번역했습니다.

**용어**를 먼저 설명하자면 이렇습니다.

![image](https://s3.us-west-2.amazonaws.com/secure.notion-static.com/6dbc7f15-8c9e-45e6-b4a4-1f9320f8dd9a/Untitled.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAT73L2G45O3KS52Y5%2F20210825%2Fus-west-2%2Fs3%2Faws4_request&X-Amz-Date=20210825T090534Z&X-Amz-Expires=86400&X-Amz-Signature=9efd39338030bea4686ae1589a4c9e003c5625b48bdcb3653df023a6e119baba&X-Amz-SignedHeaders=host&response-content-disposition=filename%20%3D%22Untitled.png%22)

`client` : 사용자 정보를 얻고자하는, 내가 만들고 있는 서버입니다.

`resource owner` : 내 서비스를 이용하기 위해 정보 제공 동의를 받는 사용자(사람).  end-user 또는 user라고도 합니다.

`resource server` : resource owner의 정보를 가지고 있고, access token을 이용해 정보를 요청받고 응답하는 서버입니다.(ex. 구글, 카카오, 깃헙)

`authorization server` : resource owner가 성공적으로 인증하고 권한을 얻었을 때 client가 요청하면 access token을 발급해주는 서버. resource server와 같을 수도 분리돼있을 수도 있습니다.

**전체적인 순서**는 이렇습니다.

1. client가 user에게 정보 접근을 위해 **권한**을 요청합니다.
2. user가 권한을 승인하면, client는 권한을 부여받습니다.
3. client는 client고유의 id와 부여받은 권한을 제시하면서 authorization server에 **access token**을 요청합니다.
4. client의 id가 인증되고 부여받은 권한이 유효하면 authorization server는 access token을 발급해줍니다. Authroization 끝남
5. client는 이제 resource server에게 access token을 이용해 **사용자의 정보**를 요청합니다.
6. 만약 access token이 유효하면 resource server는 client에게 사용자의 정보를 제공합니다.

위 예제에서 사용한 resource server는 **구글, 카카오** 이므로 이를 넣어 생각하면 됩니다.

# **4. 겪었던 이슈**

## **H2 설정**

의존성

```html
runtimeOnly 'com.h2database:h2'
```

`application.properties`

```html
# H2
spring.h2.console.enabled=true
spring.h2.console.path=/h2

# Datasource
spring.datasource.hikari.driver-class-name=org.h2.Driver
spring.datasource.hikari.jdbc-url=jdbc:h2:mem:testdb
spring.datasource.hikari.username=sa
spring.datasource.hikari.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
```

위처럼 설정하면 인메모리db를 생성할 수 있고 `/h2` 로 접근하면 db 구성을 웹에서 확인할 수 있다.

## **Mustache 변수 이름 username 충돌**

루트 경로로 접근시 세션에 사용자 정보가 있는지 확인 후, 값이 있다면 `username` 또는 `userName` 으로 뷰에 전달해줬는데 이상하게 `kimhanui`가 출력되었다. (전달한 값은 `김하늬`)

원인은 모르겠지만 찾아보니 `Windows 사용시 username변수에서 OS 사용자 이름이 출력되는` Mustache의 버그? 인듯 하다.

이를 방지하려면 `username` (대소문자 변화포함) 대신 `name` 같은 변수명을 사용하는게 좋겠다.

```java
@GetMapping("/")
public String index(Model model) {
    SessionUser user = (SessionUser) httpSession.getAttribute("user");
    if(user != null){
        model.addAttribute("name", user.getName());
    }
    return "index";
}
```

```html
<h1>Springboot Oauth2 실습</h1>
<div >
    <!--로그인 성공-->
    {{#name}} <!--username 또는 userName은 OS사용자 이름을 출력하기 때문에 대체-->
    <p>
        Logged in as : <span id="user">{{name}}</span>
        <br> <a href="/logout" role="button">Logout</a>
    </p>
    {{/name}}
    {{^name}}
    <!-- 로그인 전 -->
    <a href="/oauth2/authorization/google" >Google Login</a>
    <br><a href="/oauth2/authorization/kakao">Kakao Login</a>
    {{/name}}
</div>
<br>
```
