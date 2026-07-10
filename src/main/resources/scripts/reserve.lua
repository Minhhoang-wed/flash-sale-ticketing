-- Reserve 1 vé, atomic toàn khối (Ngày 6)
-- KEYS[1] = stock:event:{id}
-- KEYS[2] = purchased:event:{id}   (SET các userId đã giữ chỗ/mua)
-- ARGV[1] = userId
-- Trả về: >= 0 = số vé còn lại sau khi trừ (thành công)
--         -1   = hết vé
--         -2   = user này đã mua rồi (rule 1 vé/người)

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -2
end

local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
if stock <= 0 then
    return -1
end

redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
return stock - 1
