package cn.yzh.hotpot.web.controller;

import cn.yzh.hotpot.dao.projection.PersonScoreProjection;
import cn.yzh.hotpot.exception.ConnectWechatException;
import cn.yzh.hotpot.pojo.dto.OptionDto;
import cn.yzh.hotpot.pojo.dto.ResponseDto;
import cn.yzh.hotpot.pojo.entity.UserEntity;
import cn.yzh.hotpot.service.UserService;
import cn.yzh.hotpot.util.JWTUtil;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;

@RestController
@RequestMapping("/user")
public class UserController {
    private final String WECHAR_CODE = "code";

    private UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseDto login(@RequestBody String json)
            throws UnsupportedEncodingException, ConnectWechatException {
        JSONObject jsonObject = new JSONObject(json);
        String code = jsonObject.getString(WECHAR_CODE);

        OptionDto<Boolean, String> token = userService.login(code);
        if (token != null) {
            return ResponseDto.succeed()
                    .setData("isNew", token.getOptKey())
                    .setData("token", token.getOptVal());
        } else {
            return ResponseDto.failed("Code is wrong.");
        }
    }

    /**
     * 用户上传信息
     */
    @PutMapping("/info")
    public ResponseDto updateInfo(@RequestBody UserEntity user, HttpServletRequest request) {
        if (user.getAvatar() == null ||
                user.getBirthday() == null ||
                user.getCollage() == null ||
                user.getGender() == null ||
                user.getGrade() == null) {
            return ResponseDto.failed("Something is Blank.");
        }

        Integer userId = (Integer) request.getAttribute(JWTUtil.USER_ID_KEY);

        user.setId(userId);
        userService.updateInfo(user);

        return ResponseDto.succeed();
    }

    /**
     * 个人中心-个人积分
     */
    @GetMapping("/score")
    public ResponseDto getScore(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute(JWTUtil.USER_ID_KEY);
        PersonScoreProjection personScore = userService.getScore(userId);
        return ResponseDto.succeed().setData("score", personScore);
    }
}