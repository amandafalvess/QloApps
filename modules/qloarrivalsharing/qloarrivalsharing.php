<?php

if (!defined('_PS_VERSION_')) {
    exit;
}

class QloArrivalSharing extends Module
{
    public function __construct()
    {
        $this->name = 'qloarrivalsharing';
        $this->tab = 'hotel_reservation';
        $this->version = '1.0.0';
        $this->author = 'QloApps';
        $this->need_instance = 0;
        $this->bootstrap = true;

        parent::__construct();

        $this->displayName = $this->l('Chegada e Traslado de Hóspedes');
        $this->description = $this->l('Geofencing e cálculo de proximidade para recepção.');
    }

    public function install()
    {
        return parent::install() && $this->installTab();
    }

    public function uninstall()
    {
        return $this->uninstallTab() && parent::uninstall();
    }

    public function installTab()
    {
        $tab = new Tab();
        $tab->active = 1;
        $tab->class_name = 'AdminArrivalSharing';
        $tab->name = array();
        foreach (Language::getLanguages(false) as $lang) {
            $tab->name[$lang['id_lang']] = 'Recepção / Traslado';
        }
        $tab->id_parent = (int) Tab::getIdFromClassName('AdminParentOrders');
        $tab->module = $this->name;

        return (bool) $tab->add();
    }

    public function uninstallTab()
    {
        $idTab = (int) Tab::getIdFromClassName('AdminArrivalSharing');
        if ($idTab) {
            $tab = new Tab($idTab);
            return (bool) $tab->delete();
        }

        return true;
    }
}
