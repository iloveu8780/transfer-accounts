## 题目

编写2个用户之间的转账接口及其内部实现？
   **要求**：完成接口设计、并实现其内部逻辑，以完成A用户转账给B用户的功能。2个用户的账户不在同一个数据库下。注意需要手写代码，尽量不要用伪代码。
   **提示**：接口发布后会暴露给外部应用进行服务调用，请考虑接口规范、安全、幂等、重试、并发、有可能的异常分支、事务一致性、用户投诉、资金安全等的处理。
    
## 思路

### 转账流程
    
    1. 申请唯一交易流水号
    2. 拿到流水号后，发起交易请求，后端服务创建转账 task 并发送到任务队列，并返回客户端交易开始进行中
    3. 任务队列消费者从队列中拉取转账 task 处理转账流程
    4. 收款方回调，更新转账结果
    5. 再整个过程中，客户端轮询查询交易状态，直到交易成功或者失败
### 接口    
    综上，所涉及到的接口至少包括：
    
    1. 获取交易流水号接口
    2. 查询交易状态接口
    3. 发起转账交易接口
    4. 回调接口

### 考虑点
    0. 接口规范: 考虑入参类型，长度等等，比如金额用 BigdDcimal，符合 restful 风格，利用 swagger 等生成规范的 API 文档，包含语义版本号，元数据及描述等等
    1.安全：入参加密传输保证接口安全
    2.幂等、重试：全局唯一交易流水号，创建转账 task 前先查看是否已经存在交易记录（select ... for update），若存在直接返回该记录，否则就先插入新的记录
    4.并发：
        1） 用 MQ 做任务队列，创建转账 task 放入队列中，
        2） cache，交易记录等做缓存， 交易状态查询直接走redis，后台定时异步写入库中减轻数据库压力
        3） 使用乐观锁 + 事务， 对于账户加入一个 version 字段
          扣款：update table set amount = amount - #{amount},version_id =  #{versionId} + 1 where accountId = #{accountId} and version_id = #{versionId}
          存款：update table set amount = amount + #{amount},version_id =  #{versionId} + 1 where accountId = #{accountId} and version_id = #{versionId}
    5.异常分支：
        a.安全性校验不通过；
        b.参数校验不通过；
        c.账户号与转账人姓名不符
        d.账户状态不可用，如账户处于冻结、待销户等等
        e.风控管制，不可转账
        f.支付密码错误
        g.余额不足；
        h:扣款、付款失败。
     6.事务一致性：题中说明两个账户在两个不同的库中（如果类似跨行转账，还涉及扣款与付款还在不同应用中），这是一个典型的分布式事务问题，
     这里采用 MQ消息做异步入账，将整个转账过程分为扣款与收款两个阶段：
        扣款：
            1） 先同步发送 prepare 消息到队列（prepare 消息对消费者不可见或者消费者过滤该类型消息）并拿到消息的地址，如果发送失败，则直接返回转账失败；
            2） 拿到消息地址后，执行本地扣款事务（扣款+更新交易状态为“已扣款”），同样如果事务失败，将整体回滚
            3） 本地扣款事务执行成功，向 MQ 异步发送确认扣款信息，
         收款：
            1) 从队列中拉取扣款消息，调用第三方账户，更新将状态置为“收款中”，等待回调
            2）收到回调结果
            2）收款人金额增加，在收款方的库存入转账记录，并状态置为成功。如果失败，则多次重试（最大重试次数可以配置),则将交易状态置为 FAILED
         定时补偿：
            1） 针对对于扣款步骤 4 发送确认扣款消息丢失时，可以以定时任务拿到队列中所有 prepare 的消息，再查询扣款状态，如果扣款成功，则将消息置为 DONE；
            2） 当重试收款失败次数超过设置的最大次数时，可以通过定时任务扫描所有收款类型（RECEIPT）的转账记录，再向扣款账户发起反向冲正交易；
 
     7.用户投诉：针对不同的异常分支，可以用枚举或者错误码记录异常类型，用于反馈，记录好关键交易日志，
        返回交易凭证，全程交易链路可通过交易流水号跟踪追溯
     8.资金安全：先扣款，再付款，保证资金安全；
     
