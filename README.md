````markdown
# SWD392_GROUP_5_FA_26_SE1929

Nơi những anh tài hội tụ

## 1. Giới thiệu dự án

Đây là hệ thống hỗ trợ tổ chức và thực hiện thi vấn đáp với sự hỗ trợ của trí tuệ nhân tạo (AI).

Hệ thống cho phép giảng viên xây dựng ngân hàng câu hỏi, tạo và quản lý các kỳ thi vấn đáp, tổ chức phiên phỏng vấn với sinh viên thông qua AI, hỗ trợ chấm điểm bằng AI và cung cấp báo cáo kết quả.

AI đóng vai trò là trợ lý hỗ trợ trong quá trình thi và chấm điểm. Giảng viên vẫn là người quyết định điểm cuối cùng.

Hệ thống tập trung vào mô hình thi vấn đáp trong đó:

- AI đóng vai trò giám khảo ảo.
- AI đọc câu hỏi bằng Text-to-Speech (TTS).
- Sinh viên trả lời bằng giọng nói.
- Hệ thống chuyển giọng nói thành văn bản bằng Speech-to-Text (STT).
- AI phân tích câu trả lời.
- AI có thể tạo câu hỏi phụ dựa trên nội dung câu trả lời.
- AI hỗ trợ đề xuất điểm và nhận xét.
- Giảng viên có quyền xem, điều chỉnh và xác nhận điểm cuối cùng.
- Hệ thống lưu lại transcript, câu hỏi, câu trả lời, điểm và các sự kiện liên quan để phục vụ kiểm tra và minh bạch kết quả.

---

# 2. Mục tiêu hệ thống

Hệ thống được xây dựng nhằm:

1. Quản lý ngân hàng câu hỏi vấn đáp.
2. Quản lý rubric và tiêu chí chấm điểm.
3. Hỗ trợ giảng viên tạo câu hỏi thủ công, import câu hỏi hoặc sử dụng AI để tạo câu hỏi.
4. Sử dụng RAG để hỗ trợ sinh câu hỏi dựa trên tài liệu môn học.
5. Quản lý môn học, giảng viên và quyền truy cập.
6. Tạo và quản lý kỳ thi vấn đáp.
7. Quản lý lịch thi và thời gian thi của từng sinh viên.
8. Tự động lựa chọn câu hỏi cho từng sinh viên nhằm hạn chế việc lặp lại câu hỏi.
9. Thực hiện phỏng vấn vấn đáp thông qua AI.
10. Hỗ trợ Text-to-Speech và Speech-to-Text.
11. Cho phép AI tạo câu hỏi follow-up dựa trên câu trả lời.
12. Hỗ trợ AI đánh giá câu trả lời dựa trên rubric.
13. Cho phép giảng viên điều chỉnh và xác nhận điểm cuối cùng.
14. Lưu transcript, recording và lịch sử thao tác.
15. Cung cấp báo cáo kết quả cho sinh viên và giảng viên.
16. Hỗ trợ thống kê kết quả của lớp.
17. Hỗ trợ xuất bảng điểm.
18. Quản lý tài khoản, quyền truy cập và cấu hình hệ thống.

---

# 3. Kiến trúc hệ thống

Project sử dụng kiến trúc:

**Modular Monolith**

Toàn bộ hệ thống được triển khai trong một Spring Boot application nhưng được chia thành nhiều module nghiệp vụ độc lập.

```text
                    AIVIVA SYSTEM
                         |
                 Spring Boot Application
                         |
        +----------------+----------------+
        |                |                |
      Auth             User             System
        |
        +------------------------------------------+
        |                                          |
     Question                                    Exam
        |                                          |
        |                                      Interview
        |                                          |
        |                                      Evaluation
        |                                          |
        +------------------------------------------+
                                                   |
                                             Monitoring
                                                   |
                                                Report
````

Mục tiêu của việc chia module là:

* Tách biệt nghiệp vụ.
* Giảm sự phụ thuộc giữa các chức năng.
* Dễ phát triển song song theo nhóm.
* Dễ kiểm thử.
* Dễ bảo trì.
* Dễ mở rộng trong tương lai.
* Có thể tách một số module thành microservice nếu hệ thống cần mở rộng ở các giai đoạn sau.

Project hiện tại không triển khai theo microservices. Các module vẫn chạy trong cùng một application và sử dụng chung database.

---

# 4. Project Structure

Package root của project:

```text
com.swd392.aiviva
```

Cấu trúc tổng quát:

```text
src/
└── main/
    ├── java/
    │   └── com/
    │       └── swd392/
    │           └── aiviva/
    │               │
    │               ├── AiVivaApplication.java
    │               │
    │               ├── auth/
    │               │   ├── controller/
    │               │   ├── service/
    │               │   ├── repository/
    │               │   ├── entity/
    │               │   ├── dto/
    │               │   ├── security/
    │               │   └── exception/
    │               │
    │               ├── user/
    │               │   ├── controller/
    │               │   ├── service/
    │               │   ├── repository/
    │               │   ├── entity/
    │               │   ├── dto/
    │               │   ├── mapper/
    │               │   └── enums/
    │               │
    │               ├── question/
    │               │   ├── controller/
    │               │   ├── service/
    │               │   ├── repository/
    │               │   ├── entity/
    │               │   ├── dto/
    │               │   ├── mapper/
    │               │   ├── enums/
    │               │   └── ai/
    │               │
    │               ├── exam/
    │               │   ├── controller/
    │               │   ├── service/
    │               │   ├── repository/
    │               │   ├── entity/
    │               │   ├── dto/
    │               │   ├── mapper/
    │               │   ├── enums/
    │               │   └── selection/
    │               │
    │               ├── interview/
    │               │   ├── controller/
    │               │   ├── service/
    │               │   ├── repository/
    │               │   ├── entity/
    │               │   ├── dto/
    │               │   ├── mapper/
    │               │   ├── enums/
    │               │   ├── ai/
    │               │   │   ├── llm/
    │               │   │   ├── stt/
    │               │   │   ├── tts/
    │               │   │   └── followup/
    │               │   └── exception/
    │               │
    │               ├── evaluation/
    │               │   ├── controller/
    │               │   ├── service/
    │               │   ├── repository/
    │               │   ├── entity/
    │               │   ├── dto/
    │               │   ├── mapper/
    │               │   ├── enums/
    │               │   └── ai/
    │               │
    │               ├── monitoring/
    │               │   ├── controller/
    │               │   ├── service/
    │               │   ├── repository/
    │               │   ├── entity/
    │               │   ├── dto/
    │               │   ├── mapper/
    │               │   └── enums/
    │               │
    │               ├── report/
    │               │   ├── controller/
    │               │   ├── service/
    │               │   ├── repository/
    │               │   ├── dto/
    │               │   ├── mapper/
    │               │   └── export/
    │               │
    │               ├── system/
    │               │   ├── controller/
    │               │   ├── service/
    │               │   ├── repository/
    │               │   ├── entity/
    │               │   ├── dto/
    │               │   ├── mapper/
    │               │   └── enums/
    │               │
    │               └── common/
    │                   ├── exception/
    │                   ├── response/
    │                   ├── validation/
    │                   ├── security/
    │                   ├── util/
    │                   └── constant/
    │
    └── resources/
        ├── application.properties
        ├── application-dev.properties
        └── application-prod.properties
```

---

# 5. Các module chính

## 5.1. Auth Module

Package:

```text
auth/
```

Chịu trách nhiệm xác thực người dùng.

Chức năng:

* Login.
* Logout.
* Refresh token.
* Xác thực JWT.
* Kiểm tra authentication.
* Security configuration.
* Xử lý lỗi authentication.

Các thành phần chính:

```text
AuthController
AuthService
AuthServiceImpl
JwtService
SecurityConfig
LoginRequest
LoginResponse
RefreshTokenRequest
```

Auth không chịu trách nhiệm quản lý toàn bộ thông tin nghiệp vụ của User.

---

# 5.2. User Module

Package:

```text
user/
```

Chịu trách nhiệm quản lý người dùng và role.

Các role chính:

```text
ADMIN
LECTURER
STUDENT
```

Chức năng:

* Quản lý tài khoản.
* Quản lý thông tin người dùng.
* Quản lý role.
* Phân quyền theo role.
* Liên kết giảng viên với môn học.
* Liên kết sinh viên với các thông tin cần thiết của hệ thống.

Các thành phần chính:

```text
User
Role
RoleType
UserController
UserService
UserServiceImpl
UserRepository
```

---

# 5.3. Question Module

Package:

```text
question/
```

Chịu trách nhiệm quản lý ngân hàng câu hỏi và rubric.

Chức năng:

* Tạo câu hỏi.
* Chỉnh sửa câu hỏi.
* Xóa câu hỏi.
* Import câu hỏi.
* Tạo câu hỏi bằng AI.
* Review câu hỏi được AI tạo.
* Gắn câu hỏi với môn học.
* Gắn câu hỏi với topic.
* Gắn Bloom level.
* Quản lý trạng thái câu hỏi.
* Quản lý rubric.
* Quản lý tiêu chí rubric.

Bloom levels:

```text
REMEMBER
UNDERSTAND
APPLY
ANALYZE
```

Nguồn câu hỏi:

```text
MANUAL
IMPORTED
AI_GENERATED
```

Các entity chính:

```text
Question
Rubric
RubricCriterion
Subject
Topic
```

Các enum chính:

```text
BloomLevel
QuestionStatus
QuestionSource
```

AI question generation được thiết kế thông qua interface/service riêng để có thể thay đổi AI provider mà không ảnh hưởng trực tiếp đến business logic.

---

# 5.4. Exam Module

Package:

```text
exam/
```

Chịu trách nhiệm quản lý kỳ thi và lịch thi.

Chức năng:

* Tạo kỳ thi.
* Chỉnh sửa kỳ thi.
* Thiết lập môn học.
* Thêm sinh viên vào kỳ thi.
* Thiết lập thời gian thi.
* Thiết lập số lượng câu hỏi chính.
* Thiết lập số lượng câu hỏi follow-up tối đa.
* Thiết lập chiến lược lựa chọn câu hỏi.
* Quản lý trạng thái kỳ thi.
* Quản lý lịch thi.
* Quản lý danh sách sinh viên tham gia.

Các entity chính:

```text
Exam
ExamSession
ExamParticipant
ExamQuestion
ExamSchedule
```

Các enum chính:

```text
ExamStatus
ParticipantStatus
QuestionSelectionStrategy
```

Module này chịu trách nhiệm quản lý cấu trúc và trạng thái kỳ thi.

Module này không trực tiếp xử lý quá trình AI phỏng vấn.

---

# 5.5. Interview Module

Package:

```text
interview/
```

Đây là module xử lý lõi của quá trình phỏng vấn AI.

Chức năng:

* Khởi tạo phiên phỏng vấn.
* Hiển thị câu hỏi.
* Đọc câu hỏi bằng TTS.
* Nhận câu trả lời bằng voice.
* Chuyển voice thành text bằng STT.
* Lưu transcript.
* Phân tích câu trả lời.
* Xác định câu trả lời có thiếu, sai hoặc chưa rõ hay không.
* Sinh câu hỏi follow-up.
* Giới hạn thời gian trả lời.
* Giới hạn số lượng follow-up.
* Quản lý trạng thái phỏng vấn.

Các entity chính:

```text
Interview
InterviewQuestion
InterviewAnswer
FollowUpQuestion
Transcript
VoiceRecording
```

Các enum:

```text
InterviewStatus
InterviewQuestionStatus
AnswerStatus
SpeakerType
```

---

# 5.6. AI Integration trong Interview

AI được thiết kế thông qua interface để tách business logic khỏi AI provider cụ thể.

Các interface chính:

```text
SpeechToTextService
TextToSpeechService
AnswerAnalysisService
FollowUpQuestionService
InterviewAIService
```

Luồng xử lý:

```text
Interview
    |
    +-- TextToSpeechService
    |       |
    |       +-- TTS Provider
    |
    +-- SpeechToTextService
    |       |
    |       +-- STT Provider
    |
    +-- AnswerAnalysisService
    |       |
    |       +-- LLM Provider
    |
    +-- FollowUpQuestionService
            |
            +-- LLM Provider
```

Business logic không được gọi trực tiếp SDK của AI provider.

Ví dụ:

```text
Interview Service
        |
        v
SpeechToTextService
        |
        v
STT Provider Adapter
        |
        v
External STT API
```

Nếu sau này thay đổi AI provider, chỉ cần thay đổi adapter tương ứng.

---

# 5.7. Evaluation Module

Package:

```text
evaluation/
```

Chịu trách nhiệm hỗ trợ đánh giá và chấm điểm.

Chức năng:

* Nhận transcript.
* Đối chiếu câu trả lời với rubric.
* Phân tích câu trả lời.
* Đề xuất điểm bằng AI.
* Sinh nhận xét.
* Hiển thị điểm AI đề xuất cho giảng viên.
* Cho phép giảng viên điều chỉnh điểm.
* Lưu điểm cuối cùng của giảng viên.

Các entity chính:

```text
Evaluation
QuestionEvaluation
AIScoreSuggestion
LecturerFinalScore
EvaluationComment
```

Các enum:

```text
EvaluationStatus
ScoreSource
```

Nguồn điểm:

```text
AI_SUGGESTED
LECTURER_ADJUSTED
LECTURER_FINAL
```

Nguyên tắc:

```text
AI Suggested Score
        |
        v
Lecturer Review
        |
        v
Lecturer Final Score
```

AI không có quyền quyết định điểm cuối cùng.

---

# 5.8. Monitoring Module

Package:

```text
monitoring/
```

Chịu trách nhiệm lưu lại các dữ liệu phục vụ giám sát và minh bạch.

Chức năng:

* Ghi nhận các event trong kỳ thi.
* Ghi nhận thao tác quan trọng.
* Lưu recording.
* Lưu lịch sử thay đổi điểm.
* Lưu lịch sử hoạt động của AI.
* Hỗ trợ kiểm tra khi có tranh chấp điểm.

Các entity chính:

```text
AuditLog
ExamRecording
ExamEvent
InterviewEvent
```

Các event chính:

```text
EXAM_STARTED
QUESTION_PRESENTED
ANSWER_STARTED
ANSWER_SUBMITTED
FOLLOW_UP_GENERATED
AI_SCORE_GENERATED
LECTURER_SCORE_UPDATED
EXAM_FINISHED
```

---

# 5.9. Report Module

Package:

```text
report/
```

Chịu trách nhiệm tạo báo cáo và thống kê.

Đối với Student:

* Xem kết quả thi.
* Xem điểm từng câu.
* Xem nhận xét AI.
* Xem kết quả cuối cùng.

Đối với Lecturer:

* Xem kết quả của lớp.
* Xem phân phối điểm.
* Xem tỷ lệ trả lời đúng.
* Xem các câu hỏi có tỷ lệ sai cao.
* Xem thống kê kết quả.
* Xuất bảng điểm.

Các service chính:

```text
ReportService
ReportServiceImpl
ScoreStatisticsService
GradeExportService
```

---

# 5.10. System Module

Package:

```text
system/
```

Chịu trách nhiệm quản lý các cấu hình hệ thống.

Chức năng:

* Cấu hình hệ thống.
* Cấu hình ngôn ngữ.
* Quản lý quyền giảng viên đối với môn học.
* Cấu hình VI/EN.
* Các thiết lập hệ thống khác.

Các entity chính:

```text
SystemConfiguration
LanguageConfiguration
SubjectAssignment
```

Language:

```text
VI
EN
```

---

# 5.11. Common Module

Package:

```text
common/
```

Chứa các thành phần dùng chung giữa các module.

Chỉ đặt các thành phần thực sự dùng chung tại đây.

Các nhóm:

```text
common/
├── exception/
├── response/
├── validation/
├── security/
├── util/
└── constant/
```

Các thành phần ví dụ:

```text
ApiResponse
ErrorResponse
GlobalExceptionHandler
BaseEntity
PageResponse
ResourceNotFoundException
BusinessException
```

Common không được chứa business logic của từng module.

---

# 6. Nguyên tắc phụ thuộc giữa các module

Các module phải được thiết kế với mức độ phụ thuộc thấp.

Nguyên tắc:

```text
Controller
    |
    v
Service
    |
    v
Repository
    |
    v
Entity
```

Controller không được truy cập trực tiếp Repository.

Ví dụ:

```text
QuestionController
        |
        v
QuestionService
        |
        v
QuestionRepository
        |
        v
Question
```

Không được:

```text
QuestionController
        |
        v
QuestionRepository
```

---

# 7. Quy tắc giao tiếp giữa các module

Các module chỉ giao tiếp thông qua Service hoặc interface public của module.

Ví dụ:

```text
Exam
 |
 +----> Question
 |
 +----> Interview
```

Exam không được truy cập trực tiếp repository của Question hoặc Interview.

Ví dụ không nên:

```text
ExamService
    |
    +--> QuestionRepository
```

Nên:

```text
ExamService
    |
    +--> QuestionService
```

Tương tự:

```text
EvaluationService
    |
    +--> InterviewService
```

hoặc sử dụng DTO/interface phù hợp.

Mục tiêu là tránh tạo dependency trực tiếp vào implementation bên trong module khác.

---

# 8. Entity

Entity được đặt trong package:

```text
entity/
```

Ví dụ:

```text
question/entity/Question.java
exam/entity/Exam.java
interview/entity/Interview.java
evaluation/entity/Evaluation.java
```

Các entity sử dụng JPA/Hibernate để mapping với database.

Các entity dùng chung những trường cơ bản có thể kế thừa:

```text
BaseEntity
```

Ví dụ các trường:

```text
id
createdAt
updatedAt
```

Business-specific field phải được đặt trong entity tương ứng.

---

# 9. Repository

Repository được đặt trong:

```text
repository/
```

Sử dụng Spring Data JPA.

Ví dụ:

```java
public interface QuestionRepository
        extends JpaRepository<Question, Long> {
}
```

Repository chỉ chịu trách nhiệm truy cập dữ liệu.

Không đặt business logic vào Repository.

---

# 10. Service

Service chứa business logic.

Mỗi module nên sử dụng Service Interface và Service Implementation.

Ví dụ:

```text
QuestionService
QuestionServiceImpl
```

Cấu trúc:

```text
Controller
    |
    v
QuestionService
    |
    v
QuestionServiceImpl
    |
    v
QuestionRepository
```

Controller không chứa business logic phức tạp.

---

# 11. Controller

Controller chịu trách nhiệm xử lý HTTP request và response.

Ví dụ:

```java
@RestController
@RequestMapping("/api/questions")
public class QuestionController {
}
```

Controller nên:

* Nhận request.
* Validate request.
* Gọi Service.
* Trả response.

Controller không nên:

* Chứa business logic.
* Truy cập trực tiếp database.
* Gọi trực tiếp AI provider.
* Chứa logic tính điểm.

---

# 12. DTO

DTO được sử dụng để giao tiếp giữa API và client.

Ví dụ:

```text
QuestionCreateRequest
QuestionUpdateRequest
QuestionResponse
ExamCreateRequest
ExamResponse
InterviewResponse
EvaluationResponse
```

Không nên expose trực tiếp Entity ra API nếu không cần thiết.

Luồng:

```text
HTTP Request
     |
     v
Request DTO
     |
     v
Service
     |
     v
Entity
     |
     v
Response DTO
     |
     v
HTTP Response
```

---

# 13. Mapper

Mapper chịu trách nhiệm chuyển đổi giữa Entity và DTO.

Ví dụ:

```text
Question
    |
    v
QuestionMapper
    |
    v
QuestionResponse
```

Mapper không chứa business logic.

---

# 14. Functional Requirements

## FR01 - Authentication

Hệ thống cho phép người dùng đăng nhập và xác thực.

## FR02 - User Management

Admin có thể quản lý tài khoản người dùng và role.

## FR03 - Subject Management

Hệ thống quản lý môn học và phân quyền giảng viên.

## FR04 - Question Management

Lecturer có thể tạo, chỉnh sửa, xóa và quản lý câu hỏi.

## FR05 - Question Import

Lecturer có thể import câu hỏi vào ngân hàng câu hỏi.

## FR06 - AI Question Generation

Hệ thống hỗ trợ AI tạo câu hỏi dựa trên môn học, topic và tài liệu.

## FR07 - RAG Question Generation

Hệ thống có thể sử dụng tài liệu môn học làm nguồn dữ liệu cho quá trình sinh câu hỏi thông qua RAG.

## FR08 - Bloom Level

Câu hỏi được phân loại theo:

```text
Remember
Understand
Apply
Analyze
```

## FR09 - Rubric Management

Lecturer có thể tạo và quản lý rubric cho câu hỏi.

## FR10 - Exam Management

Lecturer có thể tạo và quản lý kỳ thi vấn đáp.

## FR11 - Exam Schedule

Lecturer có thể thiết lập lịch thi và thời gian cho sinh viên.

## FR12 - Question Selection

Hệ thống hỗ trợ lựa chọn câu hỏi cho từng sinh viên.

Mục tiêu là hạn chế việc các sinh viên liên tiếp nhận cùng một tập câu hỏi.

Các strategy có thể bao gồm:

```text
Random
Adaptive
```

## FR13 - Interview

Hệ thống tạo phiên phỏng vấn giữa sinh viên và AI.

## FR14 - Text-to-Speech

AI đọc câu hỏi cho sinh viên.

## FR15 - Speech-to-Text

Hệ thống chuyển câu trả lời bằng giọng nói thành văn bản.

## FR16 - Adaptive Follow-up

AI phân tích câu trả lời và có thể tạo câu hỏi follow-up khi câu trả lời:

* Không đầy đủ.
* Không rõ ràng.
* Mâu thuẫn.
* Thiếu thông tin cần thiết.

Số lượng follow-up bị giới hạn theo cấu hình kỳ thi.

## FR17 - AI Evaluation

AI phân tích câu trả lời dựa trên rubric và đưa ra điểm đề xuất.

## FR18 - Lecturer Final Score

Lecturer xem điểm AI đề xuất và có thể điều chỉnh.

Điểm cuối cùng phải do lecturer xác nhận.

## FR19 - Monitoring

Hệ thống lưu:

* Audio/video recording nếu được bật.
* Transcript.
* Câu hỏi.
* Câu trả lời.
* AI score suggestion.
* Lecturer final score.
* Exam events.
* Audit logs.

## FR20 - Student Report

Student có thể xem kết quả sau kỳ thi.

## FR21 - Lecturer Report

Lecturer có thể xem thống kê và xuất bảng điểm.

---

# 15. Luồng nghiệp vụ chính

## 15.1. Trước kỳ thi

```text
Lecturer
    |
    v
Tạo / Import / AI Generate Question
    |
    v
Review Question
    |
    v
Gắn Rubric
    |
    v
Tạo Exam
    |
    v
Chọn Subject
    |
    v
Thêm Student
    |
    v
Thiết lập Schedule
    |
    v
Thiết lập số câu hỏi
    |
    v
Thiết lập số Follow-up tối đa
    |
    v
Publish Exam
```

---

# 16. Luồng phỏng vấn AI

```text
Student
    |
    v
Start Interview
    |
    v
System Select Question
    |
    v
AI Reads Question
    |
    v
Student Answers by Voice
    |
    v
Speech-to-Text
    |
    v
Transcript
    |
    v
AI Analyzes Answer
    |
    +------------------------+
    |                        |
    v                        v
Answer Sufficient       Answer Needs Follow-up
    |                        |
    |                        v
    |                  Generate Follow-up
    |                        |
    |                        v
    |                  AI Reads Follow-up
    |                        |
    |                        v
    |                  Student Answers
    |                        |
    +------------+-----------+
                 |
                 v
          Next Main Question
                 |
                 v
            Finish Exam
```

---

# 17. Luồng chấm điểm

```text
Interview Transcript
        |
        v
AI Evaluation
        |
        v
Compare with Rubric
        |
        v
AI Score Suggestion
        |
        v
Lecturer Review
        |
        +-------------------+
        |                   |
        v                   v
Accept Score          Adjust Score
        |                   |
        +---------+---------+
                  |
                  v
        Lecturer Final Score
```

---

# 18. Nguyên tắc Human-in-the-loop

AI chỉ đóng vai trò hỗ trợ.

AI có thể:

* Đọc câu hỏi.
* Chuyển speech thành text.
* Phân tích câu trả lời.
* Tạo follow-up.
* Đề xuất điểm.
* Tạo nhận xét.

AI không được:

* Tự quyết định điểm cuối cùng.
* Thay thế quyền quyết định của lecturer.
* Tự thay đổi điểm cuối cùng đã được lecturer xác nhận.

Quy trình chấm điểm:

```text
AI Suggestion
      |
      v
Lecturer Review
      |
      v
Lecturer Final Decision
```

---

# 19. Non-functional Requirements

## 19.1. Performance

Hệ thống cần đảm bảo độ trễ đủ thấp giữa:

```text
Student Answer
      |
      v
STT
      |
      v
AI Analysis
      |
      v
Next Question
```

để quá trình thi diễn ra liên tục.

## 19.2. STT Accuracy

Speech-to-Text cần hỗ trợ tiếng Việt và hạn chế lỗi đối với thuật ngữ chuyên ngành.

## 19.3. Security

Hệ thống cần bảo vệ:

* Tài khoản.
* JWT.
* API credentials.
* Transcript.
* Audio/video recording.
* Điểm thi.
* Thông tin sinh viên.

## 19.4. Privacy

Recording và transcript chỉ được truy cập bởi các role có quyền.

## 19.5. Maintainability

Code phải được chia module rõ ràng.

Business logic không được tập trung vào một class hoặc một package duy nhất.

## 19.6. Extensibility

AI provider, STT provider và TTS provider có thể được thay đổi mà không cần thay đổi toàn bộ business logic.

---

# 20. Technology Stack

## Backend

```text
Java
Spring Boot
Spring Web
Spring Data JPA
Hibernate
Spring Security
Bean Validation
Lombok
Maven
```

## Database

```text
PostgreSQL
```

## Architecture

```text
Modular Monolith
```

## AI

Hệ thống sử dụng abstraction/interface để tích hợp:

```text
LLM
Speech-to-Text
Text-to-Speech
RAG
```

AI provider cụ thể được triển khai thông qua adapter.

---

# 21. API Structure

API sử dụng RESTful convention.

Base URL:

```text
/api
```

Ví dụ:

```text
/api/auth
/api/users
/api/questions
/api/exams
/api/interviews
/api/evaluations
/api/monitoring
/api/reports
/api/system
```

Ví dụ Question API:

```text
GET    /api/questions
GET    /api/questions/{id}
POST   /api/questions
PUT    /api/questions/{id}
DELETE /api/questions/{id}
```

Exam:

```text
GET    /api/exams
GET    /api/exams/{id}
POST   /api/exams
PUT    /api/exams/{id}
DELETE /api/exams/{id}
```

---

# 22. Authentication và Authorization

Hệ thống sử dụng JWT để xác thực request.

Luồng:

```text
User
 |
 v
Login
 |
 v
Auth Service
 |
 v
JWT
 |
 v
Client
 |
 v
API Request
 |
 v
JWT Validation
 |
 v
Authorization
 |
 v
Controller
```

Role-based authorization:

```text
ADMIN
LECTURER
STUDENT
```

Ví dụ:

```text
ADMIN
    -> User Management
    -> System Configuration

LECTURER
    -> Question Management
    -> Rubric Management
    -> Exam Management
    -> Evaluation
    -> Reports

STUDENT
    -> View Exam Schedule
    -> Take Exam
    -> View Result
```

---

# 23. Database Groups

Database được chia theo domain nghiệp vụ.

## User

```text
users
roles
```

## Question

```text
subjects
topics
questions
rubrics
rubric_criteria
```

## Exam

```text
exams
exam_sessions
exam_participants
exam_questions
exam_schedules
```

## Interview

```text
interviews
interview_questions
interview_answers
follow_up_questions
transcripts
voice_recordings
```

## Evaluation

```text
evaluations
question_evaluations
ai_score_suggestions
lecturer_final_scores
evaluation_comments
```

## Monitoring

```text
audit_logs
exam_recordings
exam_events
interview_events
```

## System

```text
system_configurations
language_configurations
subject_assignments
```

Tên bảng thực tế có thể được điều chỉnh trong quá trình thiết kế database.

---

# 24. Quy tắc xử lý lỗi

Tất cả exception nên được xử lý tập trung thông qua:

```text
GlobalExceptionHandler
```

Các exception cơ bản:

```text
ResourceNotFoundException
BusinessException
ValidationException
AuthenticationException
AuthorizationException
```

Response lỗi nên có format thống nhất.

Ví dụ:

```json
{
  "success": false,
  "message": "Question not found",
  "data": null
}
```

---

# 25. Validation

Request DTO phải sử dụng Bean Validation khi cần.

Ví dụ:

```java
@NotBlank
private String content;
```

```java
@NotNull
private Long subjectId;
```

```java
@Min(1)
private Integer maxFollowUps;
```

Validation được thực hiện trước khi business logic được xử lý.

---

# 26. Configuration

Các thông tin cấu hình được đặt trong:

```text
src/main/resources/
```

Ví dụ:

```text
application.properties
application-dev.properties
application-prod.properties
```

Các thông tin nhạy cảm không được hard-code trong source code.

Không commit:

```text
API keys
JWT secrets
Database passwords
Cloud credentials
Private tokens
```

Các giá trị này phải được cung cấp thông qua environment variables hoặc secret management.

---

# 27. AI Provider Adapter

Không được viết trực tiếp API key hoặc logic gọi provider trong Service nghiệp vụ.

Không nên:

```text
InterviewService
    |
    +--> Call External AI API directly
```

Nên:

```text
InterviewService
       |
       v
InterviewAIService
       |
       v
AI Provider Adapter
       |
       v
External AI API
```

Tương tự với STT:

```text
InterviewService
       |
       v
SpeechToTextService
       |
       v
STT Adapter
       |
       v
External STT API
```

Và TTS:

```text
InterviewService
       |
       v
TextToSpeechService
       |
       v
TTS Adapter
       |
       v
External TTS API
```

---

# 28. Quy tắc Git

Mỗi thành viên làm việc trên branch riêng.

Ví dụ:

```text
main
develop
feature/question-management
feature/exam-management
feature/interview-ai
feature/evaluation
feature/monitoring
feature/report
feature/auth
```

Không commit trực tiếp vào `main` nếu không cần thiết.

Quy trình:

```text
Create Branch
    |
    v
Develop
    |
    v
Commit
    |
    v
Push
    |
    v
Pull Request
    |
    v
Review
    |
    v
Merge
```

---

# 29. Commit Convention

Nên sử dụng commit message rõ ràng.

Ví dụ:

```text
feat: add question management
feat: add exam scheduling
feat: add interview session
feat: add speech to text integration
feat: add AI evaluation
feat: add student report
fix: fix question selection logic
fix: fix JWT authentication
refactor: refactor interview service
docs: update README
```

---

# 30. Quy tắc phát triển module

Mỗi thành viên khi phát triển một module cần đảm bảo:

1. Không tự ý thay đổi cấu trúc module khác.
2. Không truy cập trực tiếp Repository của module khác.
3. Không đưa business logic vào Controller.
4. Không đưa business logic vào Entity nếu không cần thiết.
5. Không hard-code API key.
6. Không commit secret.
7. Không commit file build không cần thiết.
8. Không tạo class dùng chung nếu chỉ một module sử dụng.
9. Tạo DTO cho API request/response.
10. Tạo Service cho business logic.
11. Tạo Repository cho database access.
12. Tạo Mapper khi cần chuyển đổi Entity và DTO.
13. Tạo Exception phù hợp khi business rule bị vi phạm.
14. Viết code theo cấu trúc package đã thống nhất.

---

# 31. Phân chia module cho Team

Dựa trên kiến trúc Modular Monolith, các module có thể được phân chia như sau:

```text
Member 1
    -> Question Module

Member 2
    -> Exam Module

Member 3
    -> Interview / AI Module

Member 4
    -> Evaluation Module

Member 5
    -> Monitoring / Report / System / Auth
```

Các thành viên cần phối hợp thông qua interface, DTO và contract API thay vì phụ thuộc trực tiếp vào implementation của nhau.

---

# 32. Development Flow

## Phase 1 - Project Setup

```text
Create Spring Boot Project
        |
        v
Configure Maven
        |
        v
Configure PostgreSQL
        |
        v
Configure JPA
        |
        v
Configure Security
        |
        v
Create Package Structure
```

## Phase 2 - Core Modules

```text
Auth
User
Question
Exam
```

## Phase 3 - AI Interview

```text
Interview
    |
    +-- STT
    +-- TTS
    +-- LLM
    +-- Follow-up
```

## Phase 4 - Evaluation

```text
Transcript
    |
    v
AI Evaluation
    |
    v
Score Suggestion
    |
    v
Lecturer Final Score
```

## Phase 5 - Monitoring

```text
Audit Log
Exam Event
Interview Event
Recording
```

## Phase 6 - Report

```text
Student Report
Lecturer Report
Statistics
Grade Export
```

## Phase 7 - Integration Testing

```text
Authentication
        |
        v
Question
        |
        v
Exam
        |
        v
Interview
        |
        v
Evaluation
        |
        v
Monitoring
        |
        v
Report
```

---

# 33. End-to-End Business Flow

```text
Admin
 |
 +--> Manage Users
 |
 +--> Manage System Configuration
 |
 +--> Assign Lecturer / Subject
```

```text
Lecturer
 |
 +--> Create Subject / Topic
 |
 +--> Create Question
 |
 +--> Import Question
 |
 +--> Generate Question with AI
 |
 +--> Review Question
 |
 +--> Create Rubric
 |
 +--> Create Exam
 |
 +--> Add Students
 |
 +--> Set Schedule
 |
 +--> Configure Question Selection
 |
 +--> Configure Follow-up Limit
 |
 +--> Publish Exam
```

```text
Student
 |
 +--> View Exam Schedule
 |
 +--> Start Exam
 |
 +--> Listen to Question
 |
 +--> Answer by Voice
 |
 +--> Receive Follow-up if required
 |
 +--> Continue Exam
 |
 +--> Finish Exam
 |
 +--> View Result
```

```text
System
 |
 +--> Convert Speech to Text
 |
 +--> Store Transcript
 |
 +--> Analyze Answer
 |
 +--> Generate Follow-up
 |
 +--> Generate AI Score Suggestion
 |
 +--> Store Exam Events
 |
 +--> Store Recording
```

```text
Lecturer
 |
 +--> Review Transcript
 |
 +--> Review AI Score
 |
 +--> Adjust Score if necessary
 |
 +--> Confirm Final Score
 |
 +--> View Statistics
 |
 +--> Export Grade
```

---

# 34. Quy tắc về dữ liệu điểm

Điểm được phân biệt theo nguồn:

```text
AI_SUGGESTED
LECTURER_ADJUSTED
LECTURER_FINAL
```

AI score chỉ là điểm đề xuất.

Lecturer final score là điểm được sử dụng làm kết quả chính thức.

Ví dụ:

```text
AI suggests: 7.5
        |
        v
Lecturer reviews
        |
        v
Lecturer changes: 8.0
        |
        v
Final score: 8.0
```

Hệ thống phải lưu được lịch sử thay đổi điểm để phục vụ audit.

---

# 35. Quy tắc về Recording và Transcript

Nếu recording được bật, hệ thống có thể lưu:

```text
Audio
Video
Transcript
```

Transcript cần liên kết được với:

```text
Interview
InterviewQuestion
InterviewAnswer
Student
```

Mục đích:

* Hỗ trợ chấm điểm.
* Hỗ trợ kiểm tra kết quả.
* Hỗ trợ xử lý khiếu nại.
* Làm bằng chứng cho quá trình thi.

---

# 36. Quy tắc về Follow-up Question

Follow-up question phải thuộc về câu hỏi chính tương ứng.

Ví dụ:

```text
Main Question
    |
    +--> Answer
    |
    +--> Follow-up Question 1
    |       |
    |       +--> Answer
    |
    +--> Follow-up Question 2
            |
            +--> Answer
```

Số lượng follow-up không được vượt quá giới hạn được cấu hình trong Exam.

Ví dụ:

```text
maxFollowUps = 2
```

thì một câu hỏi chính chỉ được tạo tối đa 2 follow-up.

---

# 37. Quy tắc lựa chọn câu hỏi

Question Selection được thiết kế thông qua Strategy Pattern.

Interface:

```text
QuestionSelectionStrategy
```

Implementation:

```text
RandomQuestionSelectionStrategy
AdaptiveQuestionSelectionStrategy
```

Luồng:

```text
Exam
    |
    v
QuestionSelectionStrategy
    |
    +--> Random Strategy
    |
    +--> Adaptive Strategy
```

Mục tiêu:

* Hạn chế trùng câu hỏi.
* Phân phối câu hỏi hợp lý.
* Có khả năng mở rộng thêm strategy trong tương lai.

---

# 38. Testing

Hệ thống cần được kiểm thử theo từng module.

Các nhóm test:

```text
Unit Test
Integration Test
Repository Test
Service Test
Controller Test
Security Test
AI Integration Test
```

Các chức năng quan trọng cần kiểm thử:

```text
Authentication
Authorization
Question CRUD
Rubric CRUD
Exam Creation
Exam Scheduling
Question Selection
Interview Flow
STT
TTS
Follow-up Generation
AI Evaluation
Lecturer Final Score
Report Generation
```

---

# 39. Nguyên tắc thiết kế quan trọng

## 39.1. Separation of Concerns

Mỗi class và module chỉ nên chịu trách nhiệm cho một nhóm nghiệp vụ rõ ràng.

## 39.2. Low Coupling

Hạn chế dependency trực tiếp giữa các module.

## 39.3. High Cohesion

Các thành phần liên quan đến cùng một nghiệp vụ được đặt trong cùng module.

## 39.4. Dependency Inversion

Business logic không phụ thuộc trực tiếp vào AI provider hoặc external service.

## 39.5. Human-in-the-loop

AI hỗ trợ nhưng lecturer là người quyết định điểm cuối cùng.

## 39.6. Security First

Không commit secret hoặc API key vào repository.

## 39.7. Extensibility

Thiết kế interface cho những thành phần có khả năng thay đổi trong tương lai, đặc biệt:

```text
LLM
STT
TTS
Question Selection
AI Evaluation
```

---

# 40. Trạng thái phát triển

Project được phát triển theo từng module.

Thứ tự triển khai dự kiến:

```text
[ ] Project Setup
[ ] Auth Module
[ ] User Module
[ ] Question Module
[ ] Exam Module
[ ] Interview Module
[ ] STT Integration
[ ] TTS Integration
[ ] LLM Integration
[ ] Follow-up Question
[ ] Evaluation Module
[ ] Monitoring Module
[ ] Report Module
[ ] System Module
[ ] Integration Testing
[ ] Deployment
```

---

# 41. Expected Final System

Sau khi hoàn thành, hệ thống sẽ hỗ trợ đầy đủ quy trình:

```text
Question Bank
      |
      v
Rubric
      |
      v
Exam Creation
      |
      v
Exam Schedule
      |
      v
AI Interview
      |
      v
Speech-to-Text
      |
      v
Answer Analysis
      |
      v
Adaptive Follow-up
      |
      v
Transcript
      |
      v
AI Score Suggestion
      |
      v
Lecturer Review
      |
      v
Lecturer Final Score
      |
      v
Monitoring
      |
      v
Student Report
      |
      v
Lecturer Statistics
      |
      v
Grade Export
```

---

# 42. Kết luận kiến trúc

Project sử dụng Modular Monolith nhằm giữ hệ thống trong một Spring Boot application nhưng vẫn đảm bảo các domain nghiệp vụ được tách biệt.

Các module chính:

```text
Auth
User
Question
Exam
Interview
Evaluation
Monitoring
Report
System
Common
```

Trong đó:

```text
Question
    -> Question Bank + Rubric + AI Question Generation

Exam
    -> Exam + Schedule + Question Selection

Interview
    -> AI Interview + STT + TTS + Follow-up

Evaluation
    -> AI Evaluation + Lecturer Final Score

Monitoring
    -> Recording + Audit + Events

Report
    -> Student Report + Lecturer Statistics + Grade Export

System
    -> System Configuration + Language + Subject Assignment

Auth
    -> Authentication + JWT

User
    -> User + Role + Permission

Common
    -> Shared Infrastructure




```
```
