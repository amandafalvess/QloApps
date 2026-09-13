<?php

if (!defined('_PS_VERSION_')) {
    exit;
}

class ArrivalBookingRepository
{
    public static function getTodayArrivals($date = null, $idHotel = null)
    {
        $targetDate = $date ? pSQL($date) : date('Y-m-d');

        $sql = new DbQuery();
        $sql->select('
            hbd.`id` AS id_htl_booking_detail,
            hbd.`id_order`,
            o.`reference` AS order_reference,
            hbd.`id_customer`,
            CONCAT(c.`firstname`, " ", c.`lastname`) AS customer_name,
            c.`email` AS customer_email,
            COALESCE(NULLIF(addr.`phone_mobile`, ""), NULLIF(addr.`phone`, ""), "-") AS customer_phone,
            hbd.`id_room`,
            IF(hbd.`room_num` != "", hbd.`room_num`, NULL) AS room_num,
            hbd.`room_type_name`,
            hbd.`id_hotel`,
            hbd.`hotel_name`,
            hbd.`adults`,
            hbd.`children`,
            (hbd.`adults` + hbd.`children`) AS total_guests,
            hbd.`date_from`,
            hbd.`date_to`,
            hbd.`check_in_time`,
            hbd.`id_status`
        ');
        $sql->from('htl_booking_detail', 'hbd');
        $sql->innerJoin('orders', 'o', 'o.`id_order` = hbd.`id_order`');
        $sql->innerJoin('customer', 'c', 'c.`id_customer` = hbd.`id_customer`');
        $sql->leftJoin('address', 'addr', 'addr.`id_address` = o.`id_address_delivery`');

        $sql->where('hbd.`date_from` >= \'' . $targetDate . ' 00:00:00\'');
        $sql->where('hbd.`date_from` <= \'' . $targetDate . ' 23:59:59\'');
        $sql->where('hbd.`is_refunded` = 0');
        $sql->where('hbd.`is_cancelled` = 0');
        $sql->where('hbd.`id_status` NOT IN (2, 3)');

        if ($idHotel) {
            $sql->where('hbd.`id_hotel` = ' . (int) $idHotel);
        }

        $sql->orderBy('hbd.`check_in_time` ASC, hbd.`id` ASC');

        $results = Db::getInstance(_PS_USE_SQL_SLAVE_)->executeS($sql);

        return is_array($results) ? $results : array();
    }
}
