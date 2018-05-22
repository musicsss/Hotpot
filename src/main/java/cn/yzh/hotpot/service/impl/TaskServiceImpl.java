package cn.yzh.hotpot.service.impl;

import cn.yzh.hotpot.dao.*;
import cn.yzh.hotpot.dao.projection.HistoryTaskListProjection;
import cn.yzh.hotpot.dao.projection.PendingTaskListProjection;
import cn.yzh.hotpot.enums.TaskFinishStatusEnum;
import cn.yzh.hotpot.pojo.dto.OptionDto;
import cn.yzh.hotpot.pojo.entity.*;
import cn.yzh.hotpot.service.TaskService;
import cn.yzh.hotpot.util.DatetimeUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskServiceImpl implements TaskService {
    private TaskDao taskDao;
    private TaskGroupDao taskGroupDao;
    private TaskItemDao taskItemDao;
    private TaskItemDayDao taskItemDayDao;
    private TaskMemberDao taskMemberDao;
    private TaskMemberDayDao taskMemberDayDao;

    @Autowired
    public TaskServiceImpl(TaskDao taskDao,
                           TaskGroupDao taskGroupDao,
                           TaskItemDao taskItemDao,
                           TaskItemDayDao taskItemDayDao,
                           TaskMemberDao taskMemberDao,
                           TaskMemberDayDao taskMemberDayDao) {
        this.taskDao = taskDao;
        this.taskGroupDao = taskGroupDao;
        this.taskItemDao = taskItemDao;
        this.taskItemDayDao = taskItemDayDao;
        this.taskMemberDao = taskMemberDao;
        this.taskMemberDayDao = taskMemberDayDao;
    }

    @Override
    public List<PendingTaskListProjection> getPendingTaskList(Integer userId) {
        System.out.println(DatetimeUtil.getNowTimestamp());
        System.out.println(DatetimeUtil.getTodayNoonTimestamp());

        return taskDao.getPendingTaskList(DatetimeUtil.getNowTimestamp(),
                DatetimeUtil.getTodayNoonTimestamp(),
                userId);
    }

    @Override
    public Page<HistoryTaskListProjection> getHistoryTaskList(Integer userId, Pageable pageable) {

        return taskDao.getHistoryTaskList(DatetimeUtil.getNowTimestamp(),
                userId,
                pageable);
    }

    @Override
    public void createTaskGroup(JSONObject jsonObject, Integer userId) {
        JSONArray items = jsonObject.getJSONArray("items");
        // 新建一个任务组
        TaskGroupEntity taskGroup = buildTaskGroup(jsonObject, userId, items.length());
        TaskGroupEntity saveGroup = taskDao.save(taskGroup);

        // 添加任务列表
        for (int i = 0; i < items.length(); i++) {
            // 添加任务内容列表
            TaskItemEntity taskItem = buildTaskItem(items.getJSONObject(i), saveGroup.getId());
            TaskItemEntity saveItem = taskItemDao.save(taskItem);
            // 添加任务每天列表
            List<TaskItemDayEntity> taskItemDays = buildTaskItemDays(saveItem, userId,
                    saveGroup.getStartTime(),
                    saveGroup.getEndTime());
            taskItemDayDao.saveAll(taskItemDays);
        }

        // 添加成员信息
        TaskMemberEntity member = buildTaskMember(userId, saveGroup.getId());
        TaskMemberEntity saveMember = taskMemberDao.save(member);
        // 添加任务内每日成员信息
        List<TaskMemberDayEntity> members = buildTaskMemberDays(saveMember,
                saveGroup.getStartTime(),
                saveGroup.getEndTime());
        taskMemberDayDao.saveAll(members);
    }

    @Override
    public OptionDto<Integer, String> finishTaskItem(JSONObject jsonObject, Integer userId) {
        Integer itemId = jsonObject.getInt("itemId");
        Integer groupId = jsonObject.getInt("groupId");

        // 更新某一项任务完成状态
        TaskItemDayEntity itemDay = taskItemDayDao.getByItemIdAndUserIdAndCurrentDay(itemId, userId, DatetimeUtil.getTodayNoonTimestamp());
        if (itemDay.getStatus().equals(TaskFinishStatusEnum.FINISHED.getValue()))
            return new OptionDto<>(1, "Task Already is Finished.");
        itemDay.setStatus(TaskFinishStatusEnum.FINISHED.getValue());
        itemDay.setFinishedTime(DatetimeUtil.getNowTimestamp());
        taskItemDayDao.save(itemDay);

        // 更新某用户某一天任务完成数
        TaskMemberDayEntity memberDay = taskMemberDayDao.getByGroupIdAndUserIdAndCurrentDay(groupId, userId,
                DatetimeUtil.getTodayNoonTimestamp());
        memberDay.setFinishedTask(memberDay.getFinishedTask() + 1);
        taskMemberDayDao.save(memberDay);
        return null;
    }

    @Override
    public OptionDto<Integer, String> joinTaskGroup(JSONObject jsonObject, Integer userId) {
        Integer groupId = jsonObject.getInt("groupId");

        TaskGroupEntity group = taskGroupDao.getById(groupId);

        // 判断是否还能容纳下更多的人
        Integer totalPeople = group.getTotalPeople();
        Integer maxPeople = group.getMaxPeople();
        if (totalPeople >= maxPeople) {
            return new OptionDto<>(1, "The Number is Full");
        }
        if (group.getEndTime().compareTo(DatetimeUtil.getNowTimestamp()) < 0) {
            return new OptionDto<>(2, "Task Group is Over.");
        }
        if (taskMemberDao.existsByUserIdAndGroupId(userId, groupId)) {
            return new OptionDto<>(3, "Already in Group.");
        }
        // 人数加 1
        group.setTotalPeople(group.getTotalPeople() + 1);
        taskGroupDao.save(group);

        Timestamp startTime = group.getStartTime().compareTo(DatetimeUtil.getNowTimestamp()) > 0 ?
                group.getStartTime() : DatetimeUtil.getNowTimestamp();

        // 添加任务项，若任务未开始，从任务开始日起添加，若任务已经开始，从当前天添加
        List<TaskItemEntity> taskItems = taskItemDao.findAllByGroupId(groupId);
        taskItems.forEach((item) -> {
            List<TaskItemDayEntity> taskItemDays = buildTaskItemDays(item, userId, startTime, group.getEndTime());
            taskItemDayDao.saveAll(taskItemDays);
        });

        // 添加成员信息
        TaskMemberEntity member = buildTaskMember(userId, groupId);
        TaskMemberEntity saveMember = taskMemberDao.save(member);
        // 添加任务内每日成员信息， 若任务未开始，从任务开始日起添加，若任务已经开始，从当前天添加
        List<TaskMemberDayEntity> members = buildTaskMemberDays(saveMember,
                startTime,
                group.getEndTime());
        taskMemberDayDao.saveAll(members);

        return null;
    }

    private List<TaskMemberDayEntity> buildTaskMemberDays(TaskMemberEntity saveMember, Timestamp startTime, Timestamp endTime) {
        List<TaskMemberDayEntity> taskMemberDays = new ArrayList<>();

        Timestamp endDay = DatetimeUtil.getNoonTimestamp(endTime);
        Timestamp curDay = DatetimeUtil.getNoonTimestamp(startTime);
        while (curDay.compareTo(endDay) <= 0) {
            TaskMemberDayEntity memberDay = new TaskMemberDayEntity();
            memberDay.setGroupId(saveMember.getGroupId());
            memberDay.setFinishedTask(0);
            memberDay.setUserId(saveMember.getUserId());
            memberDay.setCurrentDay(curDay);
            taskMemberDays.add(memberDay);

            Timestamp nextDay =  DatetimeUtil.getNextNoonTimestamp(curDay);
            curDay = DatetimeUtil.getNoonTimestamp(nextDay);
        }
        return taskMemberDays;
    }

    private TaskMemberEntity buildTaskMember(Integer userId, Integer groupId) {
        TaskMemberEntity member = new TaskMemberEntity();
        member.setGroupId(groupId);
        member.setUserId(userId);
        return member;
    }

    /**
     * 生成任务所有天的列表
     */
    private List<TaskItemDayEntity> buildTaskItemDays(TaskItemEntity saveItem,
                                                      Integer userId,
                                                      Timestamp startTime,
                                                      Timestamp endTime) {
        List<TaskItemDayEntity> taskItemDays = new ArrayList<>();

        Timestamp endDay = DatetimeUtil.getNoonTimestamp(endTime);
        Timestamp curDay = DatetimeUtil.getNoonTimestamp(startTime);
        while (curDay.compareTo(endDay) <= 0) {
            TaskItemDayEntity itemDay = buildTaskItemDay(saveItem, userId);
            itemDay.setCurrentDay(curDay);
            taskItemDays.add(itemDay);

            Timestamp nextDay =  DatetimeUtil.getNextNoonTimestamp(curDay);
            curDay = DatetimeUtil.getNoonTimestamp(nextDay);
        }
        return taskItemDays;
    }

    /**
     * 生成itemDay
     */
    private TaskItemDayEntity buildTaskItemDay(TaskItemEntity saveItem, Integer userId) {
        TaskItemDayEntity itemDay = new TaskItemDayEntity();
        itemDay.setItemId(saveItem.getId());
        itemDay.setStatus(TaskFinishStatusEnum.UNFINISHED.getValue());
        itemDay.setUserId(userId);
        return itemDay;
    }

    /**
     * 生成item
     */
    private TaskItemEntity buildTaskItem(JSONObject item, Integer groupId) {
        TaskItemEntity taskItem = new TaskItemEntity();
        taskItem.setContent(item.getString("content"));
        taskItem.setGroupId(groupId);
        return taskItem;
    }

    /**
     * 生成group
     */
    private TaskGroupEntity buildTaskGroup(JSONObject jsonObject, Integer userId, Integer totalTask) {
        TaskGroupEntity group = new TaskGroupEntity();
        group.setTitle(jsonObject.getString("title"));
        group.setType(jsonObject.getInt("type"));
        group.setSponsorId(userId);
        group.setStartTime(DatetimeUtil.long2Timestamp(jsonObject.getLong("startTime")));
        group.setEndTime(DatetimeUtil.long2Timestamp(jsonObject.getLong("endTime")));
        group.setIsPublic(jsonObject.getBoolean("isPublic"));
        group.setMaxPeople(jsonObject.getInt("maxPeople"));
        group.setTotalTask(totalTask);
        group.setTotalPeople(1);
        return group;
    }

}