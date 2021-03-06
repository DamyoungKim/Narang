package com.exp.narang.websocket.callmyname.model.manager;

import com.exp.narang.api.model.service.RoomService;
import com.exp.narang.websocket.callmyname.model.DefaultName;
import com.exp.narang.websocket.callmyname.request.NameReq;
import com.exp.narang.websocket.callmyname.request.SetNameReq;
import com.exp.narang.websocket.callmyname.response.GuessNameRes;
import com.exp.narang.websocket.callmyname.response.GameStatusRes;
import com.exp.narang.websocket.callmyname.response.SetNameRes;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class GameManager {
    private Map<String, Integer> voteStatus;
    private final Map<Long, String> nameMap;
    private final Set<Long> userIdSet;
    private final Queue<Long> userIdQueue;
    private final String defaultName [] = DefaultName.defaultName;
    private int defaultNameIdx[], defaultNum[];
    private final int playerCnt;
    private int round, nowCnt, nextCnt, voteCompleteCnt, defaultPlayerCnt;
    private static final int SETTING = 0, PLAYING = 1, DEFAULT_NAME_SIZE = DefaultName.defaultName.length;
    private static final String USER_ID = "userId", NICKNAME = "nickname", NEXT = "next";
    private long playingUserId1, playingUserId2;
    private boolean isGameStarted;

    public GameManager(Long roomId, RoomService roomService){
        log.debug("GameManager 객체 생성 ~~");
        this.playerCnt = roomService.findUserListByRoomId(roomId).size();
        nameMap = new ConcurrentHashMap<>();
        voteStatus = new HashMap<>();
        userIdSet = new HashSet<>();
        userIdQueue = new ArrayDeque<>();
        round = 0;
        nowCnt = 0;
        nextCnt = 0;
        defaultPlayerCnt = 0;
    }

    /**
     * 게임에 참여한 사용자의 userId를 저장하는 메서드
     * @param userId : 사용자의 userId
     * @return 게임을 시작할지 여부
     */
    public boolean addPlayer(long userId) {
        log.debug("callmy addPlayer 실행 ~~");
        userIdSet.add(userId);
        boolean allConnected = userIdSet.size() == playerCnt;
        // 전부 연결 되었을 때
        if(allConnected) {
            // 첫 대진표 만들기
            makeRandomDraw();
            // 이미 게임이 시작되었으면 null 반환
            if (isGameStarted) return false;
            // 게임이 시작되지 않았으면 게임 시작 표시
            isGameStarted = true;
            return true;
        }
        return false;
    }

    /**
     * 랜덤 대진표를 만드는 메서드
     */
    private void makeRandomDraw(){
        boolean[] selected = new boolean[playerCnt];
        Object[] userIdArr = userIdSet.toArray();
        int sCnt = 0;
        Random r = new Random();
        while(sCnt < playerCnt){
            int ri = r.nextInt(playerCnt);
            if(!selected[ri]){
                selected[ri] = true;
                userIdQueue.offer((long)userIdArr[ri]);
                sCnt++;
            }
        }
    }

    /**
     * Default 이름을 중복 없이 랜덤으로 지정하는 메서드
     * @return 이름
     */
    public String setDefaultName(){
        if(defaultPlayerCnt == 0) { // 첫 요청에만 한 번에 구해놓음
            defaultNum = new int[DEFAULT_NAME_SIZE];
            defaultNameIdx = new int[playerCnt];
            for(int i = 1; i < DEFAULT_NAME_SIZE; i++) defaultNum[i] = i; // 숫자 저장
            for(int i = 0; i < playerCnt; i++){ // player 별로 랜덤으로 부여된 번호 저장
                int idx = (int)(Math.random() * 100) % (DEFAULT_NAME_SIZE - i); // 0~(DEFAULT_NAME_SIZE - i - 1) 범위의 랜덤 값
                defaultNameIdx[i] = defaultNum[idx];
                defaultNum[idx] = defaultNum[DEFAULT_NAME_SIZE - i - 1];
            }
        }
        return defaultName[defaultNameIdx[defaultPlayerCnt++]];
    }

    /**
     * 정한 이름을 저장하는 메서드
     * @param req : 투표자 ID, 타겟 ID, 이름, 투표 여부, 종료 여부 가진 객체
     * @return 타겟 ID, 투표 결과 담긴 Map, 집계 상태, 최종 제시어 가진 객체
     */
    public SetNameRes setName(SetNameReq req){
        System.out.println("플레이어 수 :" + playerCnt);
        defaultPlayerCnt = 0; // 다음 사람의 defaultName 지정할 때 필요해서 지금 초기화 시킴
        // 투표 현황 관리
        if(!req.isFinished()) {
            if(req.getVote() == 1) voteStatus.put(req.getContent(), voteStatus.get(req.getContent()) + 1); // 투표
            else if(req.getVote() == -1) voteStatus.put(req.getContent(), voteStatus.get(req.getContent()) - 1); // 투표 철회
            else voteStatus.put(req.getContent(), 0); // 제시어 추가인 경우
        }
        // 개표 현황 관리
        else {
            // 모든 사람 투표 완료한 경우
            if(++voteCompleteCnt == playerCnt - 1){
                log.debug("모든 사람의 투표가 완료됨. 집계 후 result 정할 거다.");
                String result = defaultName[(int)(Math.random() * 100) % DEFAULT_NAME_SIZE]; // 0~12까지 랜덤 인덱스로 이름 들어감
                int max = -1;
                // 최다 득표 이름 찾음
                for(String content : voteStatus.keySet()){
                    if(voteStatus.get(content) > max){
                        result = content;
                        max = voteStatus.get(content);
                    }
                    log.debug("포문 도는 중 content:"+voteStatus.get(content));
                }
                log.debug("nameMap에 넣을 거다:"+result+"를!");
                log.debug("넣기 전 네임맵 사이즈:"+nameMap.size());
                log.debug("타겟아이디:"+req.getTargetId());
                nameMap.put(req.getTargetId(), result); // 최종 이름 지정
                log.debug("nameMap에 넣었다.:"+nameMap.get(req.getTargetId())+"를!");
                log.debug("넣은 후 네임맵 사이즈:"+nameMap.size());
                voteCompleteCnt = 0;
                voteStatus = new HashMap<>(); // voteStatus 초기화
                return SetNameRes.returnResult(req.getTargetId(), result, true, voteStatus);
            }
            log.debug("아직 모든 사람들의 투표가 완료되지 않음.");
        }
        return SetNameRes.returnResult(req.getTargetId(), "", false, voteStatus);
    }

    /**
     * 사용자가 자신의 이름을 맞힐 때 호출되는 메서드
     * @param req : userId와 정해진 이름이 있는 객체
     * @return 답이 맞았는지, nameMap 이 비었는지 여부를 멤버변수로 가진 객체
     */
    public GuessNameRes guessName(NameReq req){
        boolean isCorrect = nameMap.get(req.getUserId()).replace(" ", "").equals(req.getName().replace(" ", ""));
        // 맞으면
        if(isCorrect){
            // Map에서 삭제
            nameMap.remove(req.getUserId());
            // 정답자 처리
            userIdQueue.offer(req.getUserId());
            log.debug("큐에 넣음 " + req.getUserId());
            log.debug(Arrays.toString(userIdQueue.toArray()));
            // 우승 ~
            if(userIdQueue.size() == 1) return new GuessNameRes(req.getUserId(), true, true, req.getName());
        }
        return new GuessNameRes(req.getUserId(), isCorrect, nameMap.isEmpty(), req.getName());
    }

    /**
     * 현재 게임 라운드, 상태, userId, 이름 반환
     * @return GameStatusRes
     */
    public synchronized GameStatusRes getGameStatus(String type) {
        Map<String, Object> user1 = new HashMap<>();
        Map<String, Object> user2 = new HashMap<>();
        String userNick1 = "";
        String userNick2 = "";
        int status = PLAYING;

        log.debug("게임 정보 리턴~");
        // 다음 게임
        if(type.equals(NEXT)) {
            nextCnt++;
            log.debug("next 요청 횟수 " + nextCnt);
            if(nextCnt < playerCnt) return null;
            round++;
            log.debug("다음 게임ㄱㄱ");
            playingUserId1 = userIdQueue.poll();
            playingUserId2 = userIdQueue.poll();
            status = SETTING;
            log.debug("뺌 " + playingUserId1);
            log.debug("뺌 " + playingUserId2);
            log.debug(Arrays.toString(userIdQueue.toArray()));
        }else{
            nowCnt++;
            log.debug("now 요청 횟수 " + nowCnt);
            if(nowCnt < playerCnt) return null;
            log.debug("이름 정했으니 게임ㄱㄱ");
            log.debug("네임맵 사이즈:"+nameMap.size());
            userNick1 = nameMap.get(playingUserId1);
            log.debug("playingUserId1 : "+playingUserId1);
            log.debug("userNick1 : "+userNick1);
            userNick2 = nameMap.get(playingUserId2);
            log.debug("playingUserId2 : "+playingUserId2);
            log.debug("userNick2 : "+userNick2);
        }

        user1.put(USER_ID, playingUserId1);
        user1.put(NICKNAME, userNick1);

        user2.put(USER_ID, playingUserId2);
        user2.put(NICKNAME, userNick2);

        nextCnt = 0;
        nowCnt = 0;
        return GameStatusRes.of(round, status, user1, user2);
    }

    /**
     * @return userIdQueue
     */
    public Queue<Long> getUserIdQueue(){
        return userIdQueue;
    }
}