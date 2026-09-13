<?php

if (!defined('_PS_VERSION_')) {
    exit;
}

require_once dirname(__FILE__) . '/../../classes/ArrivalBookingRepository.php';

class AdminArrivalSharingController extends ModuleAdminController
{
    public function __construct()
    {
        $this->bootstrap = true;
        parent::__construct();
        $this->meta_title = $this->l('Recepção e Monitoramento de Traslados');
        $this->override_folder = '';
    }

    public function initContent()
    {
        parent::initContent();

        $today = date('Y-m-d');
        $idHotel = (int) Tools::getValue('id_hotel', (isset($this->context->cookie->id_hotel) ? $this->context->cookie->id_hotel : 0));
        $arrivals = ArrivalBookingRepository::getTodayArrivals($today, $idHotel ?: null);

        $totalGuests = 0;
        foreach ($arrivals as $arrival) {
            $totalGuests += (int) $arrival['total_guests'];
        }

        $this->context->smarty->assign(array(
            'currentDate'    => Tools::displayDate($today),
            'arrivals'       => $arrivals,
            'totalArrivals'  => count($arrivals),
            'totalGuests'    => $totalGuests,
            'orderAdminLink' => $this->context->link->getAdminLink('AdminOrders', true),
        ));

        $this->setTemplate('reception_dashboard.tpl');
    }

    public function createTemplate($tpl_name)
    {
        $templatePath = _PS_MODULE_DIR_ . $this->module->name . '/views/templates/admin/' . $tpl_name;
        if (file_exists($templatePath)) {
            return $this->context->smarty->createTemplate($templatePath, $this->context->smarty);
        }

        return parent::createTemplate($tpl_name);
    }
}
