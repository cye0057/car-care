--[[
  优惠券秒杀原子脚本（预减库存 + 一人一券）
  KEYS[1] = 库存 key   seckill:voucher:stock:{couponId}
  KEYS[2] = 成功用户集合 key  seckill:voucher:success:{couponId}
  ARGV[1] = couponId
  ARGV[2] = userId
  返回：0=抢购成功  1=库存不足  2=重复领取

  为什么必须用 Lua：「查库存→判断→扣减→记名」四步若拆成多次 Redis 调用，
  并发下两个请求可能同时通过库存判断导致超卖；Lua 在 Redis 单线程内原子执行，
  天然串行化，无需分布式锁。
]]
local stockKey = KEYS[1]
local successKey = KEYS[2]

-- 库存 key 尚未初始化时不做处理（服务端已保证先初始化再放行）
local stock = tonumber(redis.call('get', stockKey))
if stock == nil then
    return 1
end
if stock <= 0 then
    return 1
end
if redis.call('sismember', successKey, ARGV[2]) == 1 then
    return 2
end

redis.call('decr', stockKey)
redis.call('sadd', successKey, ARGV[2])
return 0
